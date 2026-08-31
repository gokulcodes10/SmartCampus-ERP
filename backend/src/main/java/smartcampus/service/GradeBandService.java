package smartcampus.service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.GradeBandRequest;
import smartcampus.dto.GradeBandResponse;
import smartcampus.entity.GradeBand;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.GradeBandRepository;

/**
 * ADMIN management of the G7 grade scale — the percentage-&gt;grade-&gt;grade-point
 * mapping every grade calculation in the application reads, so that a college on a
 * different scale changes rows here, never Java code.
 *
 * <p>Two whole-table conditions the schema cannot express as a single-row CHECK are
 * enforced here on every create/update: no two bands may overlap ({@link
 * GradeBandRepository#findOverlapping}, called with {@code excludeId = -1L} on create so
 * a brand-new band with no id yet still excludes nothing rather than accidentally
 * excluding an unrelated row — see the repository javadoc for why {@code null} would
 * silently disable the check), and no duplicate grade letter ({@code
 * uk_grade_bands_grade} backs this at the database layer too, but the pre-check gives a
 * clean message instead of a raw constraint violation in the common case).
 *
 * <p><b>Deleting the last remaining band is intentionally allowed.</b> It leaves the
 * grading module unable to produce a grade for any percentage (every {@code bandFor}
 * lookup returns empty, so grade/gradePoint become {@code null} rather than a wrong or
 * fabricated value) — a real capability loss, but not a data-integrity problem, so this
 * service does not block it.
 */
@Service
public class GradeBandService {

    private final GradeBandRepository gradeBandRepository;

    public GradeBandService(GradeBandRepository gradeBandRepository) {
        this.gradeBandRepository = gradeBandRepository;
    }

    @Transactional
    public GradeBandResponse create(GradeBandRequest request) {
        validateRange(request.minPercentage(), request.maxPercentage());
        rejectDuplicateGrade(request.grade(), null);
        rejectOverlap(request.minPercentage(), request.maxPercentage(), -1L);

        GradeBand band =
                GradeBand.builder()
                        .grade(request.grade())
                        .minPercentage(request.minPercentage())
                        .maxPercentage(request.maxPercentage())
                        .gradePoint(request.gradePoint())
                        .passGrade(request.passGrade())
                        .description(request.description())
                        .build();

        try {
            band = gradeBandRepository.save(band);
            gradeBandRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateOrOverlapException(request.grade());
        }
        return GradeBandResponse.from(band);
    }

    @Transactional
    public GradeBandResponse update(Long id, GradeBandRequest request) {
        GradeBand band = findOrThrow(id);
        validateRange(request.minPercentage(), request.maxPercentage());
        rejectDuplicateGrade(request.grade(), id);
        rejectOverlap(request.minPercentage(), request.maxPercentage(), id);

        band.setGrade(request.grade());
        band.setMinPercentage(request.minPercentage());
        band.setMaxPercentage(request.maxPercentage());
        band.setGradePoint(request.gradePoint());
        band.setPassGrade(request.passGrade());
        band.setDescription(request.description());

        try {
            gradeBandRepository.saveAndFlush(band);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateOrOverlapException(request.grade());
        }
        return GradeBandResponse.from(band);
    }

    /** Ordered {@code minPercentage} DESC (highest band first) — the scale as a student reads it top-down. */
    @Transactional(readOnly = true)
    public List<GradeBandResponse> list() {
        return gradeBandRepository.findAllByOrderByMinPercentageDesc().stream()
                .map(GradeBandResponse::from)
                .toList();
    }

    /**
     * Deleting the last remaining band is allowed — see the class javadoc — it leaves
     * grading inoperative (every percentage lookup returns no matching band) rather than
     * blocking the admin.
     */
    @Transactional
    public void delete(Long id) {
        if (!gradeBandRepository.existsById(id)) {
            throw new ResourceNotFoundException("Grade band not found: " + id);
        }
        gradeBandRepository.deleteById(id);
    }

    private void validateRange(BigDecimal minPercentage, BigDecimal maxPercentage) {
        if (minPercentage.compareTo(maxPercentage) > 0) {
            throw new BadRequestException(
                    "minPercentage (" + minPercentage + ") cannot be greater than maxPercentage ("
                            + maxPercentage + ").");
        }
    }

    private void rejectDuplicateGrade(String grade, Long excludeId) {
        gradeBandRepository
                .findByGrade(grade)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(
                        existing -> {
                            throw new DuplicateResourceException("A grade band already exists for grade \"" + grade + "\".");
                        });
    }

    /**
     * {@code excludeId} MUST be the band's own id on update, and {@code -1L} (never
     * {@code null}) on create — {@code g.id <> null} is UNKNOWN in SQL and would return
     * zero rows, silently disabling overlap detection entirely.
     */
    private void rejectOverlap(BigDecimal minPercentage, BigDecimal maxPercentage, Long excludeId) {
        List<GradeBand> overlapping =
                gradeBandRepository.findOverlapping(minPercentage, maxPercentage, excludeId);
        if (!overlapping.isEmpty()) {
            GradeBand conflict = overlapping.get(0);
            throw new DuplicateResourceException(
                    "Range [" + minPercentage + ", " + maxPercentage + "] overlaps existing grade band \""
                            + conflict.getGrade() + "\" [" + conflict.getMinPercentage() + ", "
                            + conflict.getMaxPercentage() + "].");
        }
    }

    private DuplicateResourceException duplicateOrOverlapException(String grade) {
        return new DuplicateResourceException(
                "Grade band \"" + grade + "\" conflicts with an existing grade or percentage range.");
    }

    private GradeBand findOrThrow(Long id) {
        return gradeBandRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade band not found: " + id));
    }
}
