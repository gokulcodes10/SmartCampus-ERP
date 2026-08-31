package smartcampus.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.GradeBand;

/**
 * Persistence access for {@link GradeBand}.
 *
 * <p><b>{@link #findOverlapping} — read this before calling it.</b> Callers MUST pass
 * {@code excludeId = -1L} when validating a brand-new band on CREATE, and MUST NEVER
 * pass {@code null}. In JPQL/SQL, {@code g.id <> null} evaluates to UNKNOWN for every
 * row, so the {@code WHERE} clause would match nothing and the query would silently
 * return zero rows — disabling overlap detection entirely instead of failing loudly. A
 * sentinel id that can never match a real primary key (`-1L`) is the only safe way to
 * express "no band to exclude" against this operator.
 */
public interface GradeBandRepository extends JpaRepository<GradeBand, Long> {

    Optional<GradeBand> findByGrade(String grade);

    List<GradeBand> findAllByOrderByMinPercentageDesc();

    @Query(
            "select g from GradeBand g where :percentage between g.minPercentage and g.maxPercentage "
                    + "order by g.minPercentage desc")
    List<GradeBand> findBandsFor(@Param("percentage") BigDecimal percentage);

    // On CREATE pass excludeId = -1L. NEVER pass null — `g.id <> null` is UNKNOWN in SQL
    // and the query would return zero rows, silently disabling overlap detection.
    @Query(
            "select g from GradeBand g where g.id <> :excludeId "
                    + "and g.minPercentage <= :maxPercentage and g.maxPercentage >= :minPercentage")
    List<GradeBand> findOverlapping(
            @Param("minPercentage") BigDecimal minPercentage,
            @Param("maxPercentage") BigDecimal maxPercentage,
            @Param("excludeId") Long excludeId);
}
