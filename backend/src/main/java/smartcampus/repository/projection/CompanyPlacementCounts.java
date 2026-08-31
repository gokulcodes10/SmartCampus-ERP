package smartcampus.repository.projection;

/**
 * Placement statistics grouped by company, produced by
 * {@code PlacementApplicationRepository.countGroupedByCompany}. Getter names must match
 * that query's JPQL {@code as} aliases exactly.
 */
public interface CompanyPlacementCounts {

    Long getCompanyId();

    String getCompanyName();

    long getApplications();

    long getSelected();
}
