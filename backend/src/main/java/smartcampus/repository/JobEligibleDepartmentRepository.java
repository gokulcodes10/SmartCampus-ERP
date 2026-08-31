package smartcampus.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.JobEligibleDepartment;

/**
 * Persistence access for {@link JobEligibleDepartment}.
 */
public interface JobEligibleDepartmentRepository extends JpaRepository<JobEligibleDepartment, Long> {

    /**
     * Finds all eligible departments for the given job.
     */
    List<JobEligibleDepartment> findByJobId(Long jobId);

    /**
     * Finds all eligible departments for the given jobs.
     */
    List<JobEligibleDepartment> findByJobIdIn(Collection<Long> jobIds);

    /**
     * Checks if a specific job-department pair exists.
     */
    boolean existsByJobIdAndDepartmentId(Long jobId, Long departmentId);

    /**
     * Counts eligible departments for the given department.
     */
    long countByDepartmentId(Long departmentId);

    /**
     * Deletes all eligible departments for the given job.
     *
     * <p>The caller MUST be inside a {@code @Transactional} read-write context.
     * {@code deleteByJobId} is a Spring Data derived delete method, which automatically
     * adds {@code @Modifying} at the bytecode level.
     */
    @Modifying
    void deleteByJobId(Long jobId);

    /**
     * Finds all job IDs that are eligible for the given department.
     */
    @Query("select jed.job.id from JobEligibleDepartment jed where jed.department.id = :departmentId")
    List<Long> findJobIdsByDepartmentId(@Param("departmentId") Long departmentId);
}
