package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.ApplicationStatusSlice;
import smartcampus.dto.CompanyPlacementRow;
import smartcampus.dto.DepartmentPlacementRow;
import smartcampus.dto.JobFunnelRow;
import smartcampus.dto.PlacementAnalyticsResponse;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.CompanyStatus;
import smartcampus.entity.Job;
import smartcampus.entity.JobStatus;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.CompanyRepository;
import smartcampus.repository.JobRepository;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.projection.ApplicationStatusCount;
import smartcampus.repository.projection.CompanyJobCounts;
import smartcampus.repository.projection.CompanyPlacementCounts;
import smartcampus.repository.projection.DepartmentPlacementCounts;
import smartcampus.repository.projection.JobStatusCount;

/**
 * §36: the admin placement analytics overview. Every number here traces to a database
 * aggregation query — no literal, no placeholder, no estimate (§60, §69).
 *
 * <p>Method security is not enabled on this build; {@link #overview} starts with {@link
 * ScopedWriteAuthorizer#requireAdmin}.
 */
@Service
public class PlacementAnalyticsService {

    private static final int TOP_COMPANIES_LIMIT = 5;
    private static final int JOB_FUNNEL_LIMIT = 10;

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final PlacementApplicationRepository placementApplicationRepository;
    private final StudentRepository studentRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;

    public PlacementAnalyticsService(
            CompanyRepository companyRepository,
            JobRepository jobRepository,
            PlacementApplicationRepository placementApplicationRepository,
            StudentRepository studentRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer) {
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
        this.placementApplicationRepository = placementApplicationRepository;
        this.studentRepository = studentRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
    }

    @Transactional(readOnly = true)
    public PlacementAnalyticsResponse overview(User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);

        long totalCompanies = companyRepository.count();
        long activeCompanies = companyRepository.countByStatus(CompanyStatus.ACTIVE);

        long totalJobs = jobRepository.count();
        long draftJobs = jobRepository.countByStatus(JobStatus.DRAFT);
        long openJobs = jobRepository.countByStatus(JobStatus.OPEN);
        long closedJobs = jobRepository.countByStatus(JobStatus.CLOSED);
        long cancelledJobs = jobRepository.countByStatus(JobStatus.CANCELLED);

        long totalApplications = placementApplicationRepository.count();
        long uniqueApplicants = placementApplicationRepository.countDistinctApplicants();
        long selectedStudents =
                placementApplicationRepository.countDistinctStudentsByStatus(ApplicationStatus.SELECTED);

        long activeStudents = studentRepository.count(statusSpec(StudentStatus.ACTIVE));
        BigDecimal placementRate = rate(selectedStudents, activeStudents);

        List<ApplicationStatusSlice> statusBreakdown = buildStatusBreakdown();
        List<CompanyPlacementRow> topCompanies = buildTopCompanies();
        List<DepartmentPlacementRow> departmentBreakdown = buildDepartmentBreakdown();
        List<JobFunnelRow> jobFunnel = buildJobFunnel();

        return new PlacementAnalyticsResponse(
                totalCompanies,
                activeCompanies,
                totalJobs,
                draftJobs,
                openJobs,
                closedJobs,
                cancelledJobs,
                totalApplications,
                uniqueApplicants,
                selectedStudents,
                activeStudents,
                placementRate,
                statusBreakdown,
                topCompanies,
                departmentBreakdown,
                jobFunnel);
    }

    private List<ApplicationStatusSlice> buildStatusBreakdown() {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, 0L);
        }
        for (ApplicationStatusCount row : placementApplicationRepository.countGroupedByStatus()) {
            counts.put(row.getStatus(), row.getTotal());
        }
        List<ApplicationStatusSlice> slices = new ArrayList<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            slices.add(new ApplicationStatusSlice(status, counts.get(status)));
        }
        return slices;
    }

    private List<CompanyPlacementRow> buildTopCompanies() {
        List<CompanyPlacementCounts> rows = placementApplicationRepository.countGroupedByCompany();
        List<CompanyPlacementCounts> top =
                rows.stream()
                        .sorted(
                                Comparator.comparingLong(CompanyPlacementCounts::getSelected)
                                        .thenComparingLong(CompanyPlacementCounts::getApplications)
                                        .reversed())
                        .limit(TOP_COMPANIES_LIMIT)
                        .toList();

        List<Long> companyIds = top.stream().map(CompanyPlacementCounts::getCompanyId).toList();
        Map<Long, CompanyJobCounts> jobCounts = new HashMap<>();
        if (!companyIds.isEmpty()) {
            for (CompanyJobCounts c : jobRepository.countByCompanyIds(companyIds)) {
                jobCounts.put(c.getCompanyId(), c);
            }
        }

        return top.stream()
                .map(
                        c ->
                                new CompanyPlacementRow(
                                        c.getCompanyId(),
                                        c.getCompanyName(),
                                        jobCounts.containsKey(c.getCompanyId())
                                                ? jobCounts.get(c.getCompanyId()).getTotalJobs()
                                                : 0L,
                                        c.getApplications(),
                                        c.getSelected()))
                .toList();
    }

    private List<DepartmentPlacementRow> buildDepartmentBreakdown() {
        List<DepartmentPlacementCounts> rows = placementApplicationRepository.countGroupedByDepartment();
        List<DepartmentPlacementRow> result = new ArrayList<>();
        for (DepartmentPlacementCounts row : rows) {
            long activeStudentsInDept =
                    studentRepository.count(
                            statusAndDepartmentSpec(StudentStatus.ACTIVE, row.getDepartmentId()));
            BigDecimal rate = rate(row.getSelected(), activeStudentsInDept);
            result.add(
                    new DepartmentPlacementRow(
                            row.getDepartmentId(),
                            row.getDepartmentName(),
                            activeStudentsInDept,
                            row.getApplicants(),
                            row.getSelected(),
                            rate));
        }
        return result;
    }

    private List<JobFunnelRow> buildJobFunnel() {
        List<Long> allJobIds = jobRepository.findAll().stream().map(Job::getId).toList();
        if (allJobIds.isEmpty()) {
            return List.of();
        }

        List<JobStatusCount> allCounts = placementApplicationRepository.countGroupedByJobAndStatus(allJobIds);

        Map<Long, Long> totalByJob = new HashMap<>();
        Map<Long, Map<ApplicationStatus, Long>> breakdownByJob = new HashMap<>();
        for (JobStatusCount row : allCounts) {
            totalByJob.merge(row.getJobId(), row.getTotal(), Long::sum);
            breakdownByJob
                    .computeIfAbsent(row.getJobId(), k -> new EnumMap<>(ApplicationStatus.class))
                    .put(row.getStatus(), row.getTotal());
        }

        List<Long> topJobIds =
                totalByJob.entrySet().stream()
                        .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                        .limit(JOB_FUNNEL_LIMIT)
                        .map(Map.Entry::getKey)
                        .toList();

        if (topJobIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Job> jobsById = new HashMap<>();
        for (Job job : jobRepository.findAllById(topJobIds)) {
            jobsById.put(job.getId(), job);
        }

        List<JobFunnelRow> funnel = new ArrayList<>();
        for (Long jobId : topJobIds) {
            Job job = jobsById.get(jobId);
            if (job == null) {
                continue;
            }
            Map<ApplicationStatus, Long> breakdown = breakdownByJob.getOrDefault(jobId, Map.of());
            long applicationCount = totalByJob.getOrDefault(jobId, 0L);
            long shortlisted = breakdown.getOrDefault(ApplicationStatus.SHORTLISTED, 0L);
            long selected = breakdown.getOrDefault(ApplicationStatus.SELECTED, 0L);
            long rejected = breakdown.getOrDefault(ApplicationStatus.REJECTED, 0L);
            funnel.add(
                    new JobFunnelRow(
                            jobId,
                            job.getTitle(),
                            job.getCompany().getName(),
                            applicationCount,
                            shortlisted,
                            selected,
                            rejected));
        }
        return funnel;
    }

    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static Specification<Student> statusSpec(StudentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Student> statusAndDepartmentSpec(StudentStatus status, Long departmentId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), status));
            predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
