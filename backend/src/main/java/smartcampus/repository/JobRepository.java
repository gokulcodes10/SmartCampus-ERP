package smartcampus.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.Job;
import smartcampus.entity.JobStatus;
import smartcampus.repository.projection.CompanyJobCounts;

/**
 * Persistence access for {@link Job}.
 *
 * <p>{@link JpaSpecificationExecutor} supports dynamic filtering and pagination for admin
 * listing and search screens (§44 server-side paging).
 */
public interface JobRepository extends JpaRepository<Job, Long>,
    JpaSpecificationExecutor<Job> {

    /**
     * Checks if any jobs exist for the given company.
     */
    boolean existsByCompanyId(Long companyId);

    /**
     * Counts jobs with the given status.
     */
    long countByStatus(JobStatus status);

    /**
     * Counts jobs by company, including total jobs and open jobs.
     *
     * @param companyIds the company IDs to aggregate over
     * @return list of CompanyJobCounts projections
     */
    @Query("""
        select j.company.id as companyId, count(j) as totalJobs,
               sum(case when j.status = smartcampus.entity.JobStatus.OPEN then 1 else 0 end) as openJobs
        from Job j where j.company.id in :companyIds group by j.company.id""")
    List<CompanyJobCounts> countByCompanyIds(@Param("companyIds") Collection<Long> companyIds);
}
