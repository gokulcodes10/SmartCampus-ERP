package smartcampus.repository.projection;

import smartcampus.entity.ApplicationStatus;

/**
 * Application counts grouped by status, produced by
 * {@code PlacementApplicationRepository.countGroupedByStatus}. Getter names must match
 * that query's JPQL {@code as} aliases exactly.
 */
public interface ApplicationStatusCount {

    ApplicationStatus getStatus();

    long getTotal();
}
