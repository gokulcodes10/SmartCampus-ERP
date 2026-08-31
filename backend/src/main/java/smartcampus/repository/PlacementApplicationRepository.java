package smartcampus.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.PlacementApplication;
import smartcampus.repository.projection.ApplicationStatusCount;
import smartcampus.repository.projection.CompanyPlacementCounts;
import smartcampus.repository.projection.DepartmentPlacementCounts;
import smartcampus.repository.projection.JobStatusCount;

/**
 * Persistence access for {@link PlacementApplication}.
 *
 * <p>{@link JpaSpecificationExecutor} supports dynamic filtering and pagination for admin
 * listing and search screens (§44 server-side paging).
 */
public interface PlacementApplicationRepository extends JpaRepository<PlacementApplication, Long>,
    JpaSpecificationExecutor<PlacementApplication> {

    /**
     * Finds an application by job ID and student ID.
     *
     * <p>Guards against duplicate applications (§35 guard).
     */
    Optional<PlacementApplication> findByJobIdAndStudentId(Long jobId, Long studentId);

    /**
     * Checks if an application exists for the given job and student.
     *
     * <p>Used by service layer to return a clean 409 before attempting insert.
     * Database constraint is the authoritative guard against concurrent submits.
     */
    boolean existsByJobIdAndStudentId(Long jobId, Long studentId);

    /**
     * Checks if any applications exist for the given job.
     */
    boolean existsByJobId(Long jobId);

    /**
     * Finds all applications by a student for the given jobs.
     */
    List<PlacementApplication> findByStudentIdAndJobIdIn(Long studentId, Collection<Long> jobIds);

    /**
     * Groups all applications by status and counts them.
     *
     * @return list of ApplicationStatusCount projections
     */
    @Query("select a.status as status, count(a) as total from PlacementApplication a group by a.status")
    List<ApplicationStatusCount> countGroupedByStatus();

    /**
     * Groups applications by job and status, counting each combination.
     *
     * @param jobIds the job IDs to aggregate over
     * @return list of JobStatusCount projections
     */
    @Query("""
        select a.job.id as jobId, a.status as status, count(a) as total
        from PlacementApplication a where a.job.id in :jobIds group by a.job.id, a.status""")
    List<JobStatusCount> countGroupedByJobAndStatus(@Param("jobIds") Collection<Long> jobIds);

    /**
     * Counts the number of distinct students who have applied.
     */
    @Query("select count(distinct a.student.id) from PlacementApplication a")
    long countDistinctApplicants();

    /**
     * Counts the number of distinct students with the given application status.
     */
    @Query("select count(distinct a.student.id) from PlacementApplication a where a.status = :status")
    long countDistinctStudentsByStatus(@Param("status") ApplicationStatus status);

    /**
     * Groups applications by company and counts total and selected applications.
     *
     * @return list of CompanyPlacementCounts projections, ordered for top 5 by selected then applications
     */
    @Query("""
        select a.job.company.id as companyId, a.job.company.name as companyName,
               count(a) as applications,
               sum(case when a.status = smartcampus.entity.ApplicationStatus.SELECTED then 1 else 0 end) as selected
        from PlacementApplication a group by a.job.company.id, a.job.company.name""")
    List<CompanyPlacementCounts> countGroupedByCompany();

    /**
     * Groups applications by student department, counting applicants and selected.
     *
     * @return list of DepartmentPlacementCounts projections
     */
    @Query("""
        select a.student.department.id as departmentId, a.student.department.name as departmentName,
               count(distinct a.student.id) as applicants,
               count(distinct case when a.status = smartcampus.entity.ApplicationStatus.SELECTED
                                   then a.student.id else null end) as selected
        from PlacementApplication a where a.student.department is not null
        group by a.student.department.id, a.student.department.name""")
    List<DepartmentPlacementCounts> countGroupedByDepartment();
}
