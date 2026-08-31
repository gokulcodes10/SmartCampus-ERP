package smartcampus.repository.projection;

/**
 * Placement statistics grouped by department, produced by
 * {@code PlacementApplicationRepository.countGroupedByDepartment}. Getter names must match
 * that query's JPQL {@code as} aliases exactly.
 */
public interface DepartmentPlacementCounts {

    Long getDepartmentId();

    String getDepartmentName();

    long getApplicants();

    long getSelected();
}
