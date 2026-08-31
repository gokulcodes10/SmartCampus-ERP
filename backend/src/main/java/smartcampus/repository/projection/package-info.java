/**
 * Spring Data JPA interface-based projections for aggregate queries.
 *
 * <p>Each interface here is the result shape of one {@code @Query} aggregate declared in
 * {@code smartcampus.repository} — attendance and marks totals summed/grouped in the
 * database rather than in Java. Getter names must match the JPQL {@code as} aliases in
 * the owning query exactly, or Spring Data cannot bind the projection.
 */
package smartcampus.repository.projection;
