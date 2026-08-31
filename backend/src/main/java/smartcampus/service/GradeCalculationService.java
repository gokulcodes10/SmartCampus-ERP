package smartcampus.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.SubjectGradeSummary;
import smartcampus.entity.GradeBand;
import smartcampus.repository.GradeBandRepository;

/**
 * The single place the G7 grade arithmetic is implemented — percentage, grade-band
 * lookup, and credit-weighted GPA. Deliberately free of HTTP and DTO concerns beyond the
 * {@link SubjectGradeSummary} projection it consumes, so Phase 5's {@code
 * AnalyticsService} can reuse it rather than reimplementing grading.
 *
 * <p>Nothing about grading is hard-coded here: every boundary and grade point comes from
 * {@link GradeBandRepository}, which is backed by the admin-configurable {@code
 * grade_bands} table (G7). No literal grade letter, boundary, or grade point appears in
 * this class.
 */
@Service
public class GradeCalculationService {

    /** Scale (decimal places) every percentage and GPA figure in this module is rounded to. */
    public static final int SCALE = 2;

    private final GradeBandRepository gradeBandRepository;

    public GradeCalculationService(GradeBandRepository gradeBandRepository) {
        this.gradeBandRepository = gradeBandRepository;
    }

    /**
     * {@code obtained * 100 / maximum}, scale {@value #SCALE}, {@link RoundingMode#HALF_UP}.
     * Returns {@code null} — never zero, never a division-by-zero exception — when
     * {@code maximum} is {@code null} or zero, matching the same "no denominator, no
     * fabricated figure" rule the attendance percentage formula (G6) follows.
     */
    public BigDecimal percentage(BigDecimal obtained, BigDecimal maximum) {
        if (maximum == null || maximum.signum() == 0) {
            return null;
        }
        BigDecimal safeObtained = obtained == null ? BigDecimal.ZERO : obtained;
        return safeObtained
                .multiply(BigDecimal.valueOf(100))
                .divide(maximum, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The {@link GradeBand} whose {@code [minPercentage, maxPercentage]} range contains
     * {@code percentage}. Empty when {@code percentage} is {@code null} or no band
     * matches — callers must never fabricate a letter grade in that case. If more than
     * one band matches (an admin created an overlapping pair), the one with the highest
     * {@code minPercentage} wins; {@link GradeBandRepository#findBandsFor} already
     * returns candidates ordered {@code minPercentage DESC}, so the first result is
     * exactly that band.
     */
    @Transactional(readOnly = true)
    public Optional<GradeBand> bandFor(BigDecimal percentage) {
        if (percentage == null) {
            return Optional.empty();
        }
        List<GradeBand> candidates = gradeBandRepository.findBandsFor(percentage);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    /**
     * Credit-weighted GPA: {@code sum(credits_i * gradePoint_i) / sum(credits_i)} over
     * only the subjects with a non-null {@code gradePoint}, scale {@value #SCALE},
     * {@link RoundingMode#HALF_UP}. {@code null} when no subject in {@code subjects} has
     * a computable grade (gradedCredits == 0) — never a fabricated 0.00. A single
     * 1-credit subject never counts the same as a 4-credit subject; this is credit
     * weighting, not an arithmetic mean of the per-subject percentages or grade points.
     */
    public BigDecimal creditWeightedGpa(List<SubjectGradeSummary> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return null;
        }
        BigDecimal weightedSum = BigDecimal.ZERO;
        int gradedCredits = 0;
        for (SubjectGradeSummary subject : subjects) {
            if (subject.gradePoint() != null && subject.credits() != null) {
                weightedSum =
                        weightedSum.add(BigDecimal.valueOf(subject.credits()).multiply(subject.gradePoint()));
                gradedCredits += subject.credits();
            }
        }
        if (gradedCredits == 0) {
            return null;
        }
        return weightedSum.divide(BigDecimal.valueOf(gradedCredits), SCALE, RoundingMode.HALF_UP);
    }
}
