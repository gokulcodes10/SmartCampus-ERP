package smartcampus.repository.projection;

/**
 * Aggregate counts of jobs for a company, produced by {@code JobRepository.countByCompanyIds}.
 * Getter names must match that query's JPQL {@code as} aliases exactly.
 */
public interface CompanyJobCounts {

    Long getCompanyId();

    long getTotalJobs();

    long getOpenJobs();
}
