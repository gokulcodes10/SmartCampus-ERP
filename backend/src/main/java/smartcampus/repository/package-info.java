/**
 * Spring Data JPA repositories — the persistence layer.
 *
 * <p>Repositories own all database access: derived queries, {@code @Query} methods,
 * projections, paging and sorting. They are the only components permitted to speak JPQL
 * or SQL, and they are called from {@code smartcampus.service}, never directly from a
 * controller.
 */
package smartcampus.repository;
