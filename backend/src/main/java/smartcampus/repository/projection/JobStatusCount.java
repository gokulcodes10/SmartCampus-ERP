package smartcampus.repository.projection;

import smartcampus.entity.ApplicationStatus;

/**
 * Application counts grouped by job and status, produced by
 * {@code PlacementApplicationRepository.countGroupedByJobAndStatus}. Getter names must
 * match that query's JPQL {@code as} aliases exactly.
 */
public interface JobStatusCount {

    Long getJobId();

    ApplicationStatus getStatus();

    long getTotal();
}
