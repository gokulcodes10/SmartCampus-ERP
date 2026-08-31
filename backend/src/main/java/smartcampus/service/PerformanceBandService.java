package smartcampus.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.PerformanceBandRequest;
import smartcampus.dto.PerformanceBandResponse;
import smartcampus.entity.PerformanceBand;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.PerformanceBandRepository;

/**
 * ADMIN management of the §60 performance-band thresholds — the admin-configurable rows {@link
 * PerformanceClassifier} reads to turn a student's marks/attendance/GPA into an EXCELLENT / GOOD
 * / AVERAGE / AT_RISK classification, so that changing the thresholds is a data change, never a
 * code change.
 *
 * <p><b>There is deliberately no {@code create()} and no {@code delete()}.</b> The category set
 * is closed — fixed by the {@code chk_performance_bands_category} CHECK constraint in {@code
 * V5__analytics.sql} and by the {@link smartcampus.entity.PerformanceCategory} enum — so a fifth
 * category would have no meaning to the classifier and a deleted one would leave some students
 * permanently unclassifiable against a gap in the scale. Only {@link #update(Long,
 * PerformanceBandRequest)} exists; it reconfigures thresholds on one of the four existing rows.
 *
 * <p>{@link #update} validates the WHOLE set of bands (monotonicity + catch-all invariant)
 * against an in-memory copy BEFORE calling {@code saveAndFlush} — never save-then-throw. Spring's
 * default rollback-on-unchecked-exception would roll back a save that already happened if the
 * validation threw afterwards from within the same {@code @Transactional} method, silently
 * persisting a broken (or worse, a partially-applied) set of thresholds up to the point of
 * failure. Validating a full in-memory copy first means the write only ever happens once every
 * invariant already holds.
 */
@Service
public class PerformanceBandService {

    private final PerformanceBandRepository performanceBandRepository;

    public PerformanceBandService(PerformanceBandRepository performanceBandRepository) {
        this.performanceBandRepository = performanceBandRepository;
    }

    @Transactional(readOnly = true)
    public List<PerformanceBandResponse> list() {
        return performanceBandRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(PerformanceBandResponse::from)
                .toList();
    }

    @Transactional
    public PerformanceBandResponse update(Long id, PerformanceBandRequest request) {
        List<PerformanceBand> allBands = performanceBandRepository.findAllByOrderByDisplayOrderAsc();
        PerformanceBand target =
                allBands.stream()
                        .filter(band -> band.getId().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Performance band not found: " + id));

        // Build an in-memory snapshot of every band with the requested change applied to the
        // target, and validate THAT before touching the database at all.
        List<Snapshot> snapshots =
                allBands.stream()
                        .map(
                                band ->
                                        band.getId().equals(id)
                                                ? new Snapshot(
                                                        band,
                                                        request.minMarksPercentage(),
                                                        request.minAttendancePercentage(),
                                                        request.minGpa())
                                                : new Snapshot(
                                                        band,
                                                        band.getMinMarksPercentage(),
                                                        band.getMinAttendancePercentage(),
                                                        band.getMinGpa()))
                        .collect(Collectors.toCollection(ArrayList::new));

        validateMonotonicity(snapshots);
        validateCatchAll(snapshots);

        target.setMinMarksPercentage(request.minMarksPercentage());
        target.setMinAttendancePercentage(request.minAttendancePercentage());
        target.setMinGpa(request.minGpa());
        target.setColorHex(request.colorHex());
        target.setDescription(request.description());

        PerformanceBand saved = performanceBandRepository.saveAndFlush(target);
        return PerformanceBandResponse.from(saved);
    }

    /**
     * For bands sorted by {@code displayOrder} ASC (strictest first), every band's marks and
     * attendance minimums must be {@code >=} the next (looser) band's, and wherever both bands'
     * {@code minGpa} are non-null the same must hold; a non-null {@code minGpa} on a looser band
     * while the stricter one has {@code null} is also rejected (a null GPA requirement is
     * strictly looser than any real one, so that ordering would itself be non-monotonic).
     * Otherwise the looser band could never be reached — the stricter band, tested first, would
     * always win.
     */
    private void validateMonotonicity(List<Snapshot> snapshots) {
        // `snapshots` follows `allBands`, which is already displayOrder ASC.
        for (int i = 0; i < snapshots.size() - 1; i++) {
            Snapshot stricter = snapshots.get(i);
            Snapshot looser = snapshots.get(i + 1);

            boolean violates =
                    stricter.minMarksPercentage().compareTo(looser.minMarksPercentage()) < 0
                            || stricter.minAttendancePercentage().compareTo(looser.minAttendancePercentage()) < 0
                            || (stricter.minGpa() != null
                                    && looser.minGpa() != null
                                    && stricter.minGpa().compareTo(looser.minGpa()) < 0)
                            || (stricter.minGpa() == null && looser.minGpa() != null);

            if (violates) {
                throw new BadRequestException(
                        "Performance band "
                                + stricter.band().getCategory().name()
                                + " would be unreachable: its thresholds must not be lower than "
                                + looser.band().getCategory().name()
                                + "'s.");
            }
        }
    }

    /**
     * The band with the highest {@code displayOrder} must keep its minimums at 0/0/null, or a
     * student with real results could match no band at all.
     */
    private void validateCatchAll(List<Snapshot> snapshots) {
        Snapshot catchAll = snapshots.get(snapshots.size() - 1);
        boolean valid =
                catchAll.minMarksPercentage().compareTo(BigDecimal.ZERO) == 0
                        && catchAll.minAttendancePercentage().compareTo(BigDecimal.ZERO) == 0
                        && catchAll.minGpa() == null;
        if (!valid) {
            throw new BadRequestException(
                    "The "
                            + catchAll.band().getCategory().name()
                            + " band is the catch-all: its minimums must stay 0 and it must not require a GPA, or"
                            + " students with real results would match no band at all.");
        }
    }

    /** An in-memory (band, proposed-thresholds) pair used to validate the whole set pre-save. */
    private record Snapshot(
            PerformanceBand band,
            BigDecimal minMarksPercentage,
            BigDecimal minAttendancePercentage,
            BigDecimal minGpa) {}
}
