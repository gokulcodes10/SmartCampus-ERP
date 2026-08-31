package smartcampus.service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.PerformanceClassificationResponse;
import smartcampus.entity.PerformanceBand;
import smartcampus.repository.PerformanceBandRepository;

/**
 * Turns a student's (or a single subject row's) real marks percentage, attendance percentage and
 * optional GPA into one of the four admin-configured {@link smartcampus.entity.PerformanceCategory}
 * values (§22-§24, §60). Every number driving the decision is a row in {@code
 * performance_bands}, read fresh on every call via {@link
 * PerformanceBandRepository#findAllByOrderByDisplayOrderAsc()} — no threshold, colour or
 * category boundary is a literal here.
 *
 * <p>§69 — an unclassifiable student (missing marks and/or attendance, or a set of bands that
 * matches nothing) is NEVER defaulted into {@code AT_RISK}. That would be a fabricated verdict.
 * Instead every field but {@code reason} comes back {@code null} and {@code reason} says exactly
 * why.
 */
@Service
public class PerformanceClassifier {

    private final PerformanceBandRepository performanceBandRepository;

    public PerformanceClassifier(PerformanceBandRepository performanceBandRepository) {
        this.performanceBandRepository = performanceBandRepository;
    }

    @Transactional(readOnly = true)
    public PerformanceClassificationResponse classify(
            BigDecimal marksPercentage, BigDecimal attendancePercentage, BigDecimal gpa) {

        if (marksPercentage == null || attendancePercentage == null) {
            String reason;
            if (marksPercentage == null && attendancePercentage == null) {
                reason =
                        "Not enough data to classify: this student has no graded marks and no held classes yet.";
            } else if (marksPercentage == null) {
                reason = "Not enough data to classify: this student has no graded marks yet.";
            } else {
                reason = "Not enough data to classify: this student has no held classes yet.";
            }
            return new PerformanceClassificationResponse(
                    null, null, null, marksPercentage, attendancePercentage, gpa, reason);
        }

        List<PerformanceBand> bands = performanceBandRepository.findAllByOrderByDisplayOrderAsc();
        for (PerformanceBand band : bands) {
            boolean marksMatch = marksPercentage.compareTo(band.getMinMarksPercentage()) >= 0;
            boolean attendanceMatch = attendancePercentage.compareTo(band.getMinAttendancePercentage()) >= 0;
            boolean gpaMatch = band.getMinGpa() == null || (gpa != null && gpa.compareTo(band.getMinGpa()) >= 0);

            if (marksMatch && attendanceMatch && gpaMatch) {
                return new PerformanceClassificationResponse(
                        band.getCategory(),
                        band.getColorHex(),
                        band.getDescription(),
                        marksPercentage,
                        attendancePercentage,
                        gpa,
                        "Meets the " + band.getCategory().name() + " thresholds.");
            }
        }

        return new PerformanceClassificationResponse(
                null,
                null,
                null,
                marksPercentage,
                attendancePercentage,
                gpa,
                "No configured performance band matches these figures. Ask an admin to review the performance"
                        + " bands.");
    }
}
