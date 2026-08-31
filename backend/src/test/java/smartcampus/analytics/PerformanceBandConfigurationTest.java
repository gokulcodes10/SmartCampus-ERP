package smartcampus.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import smartcampus.TestcontainersConfiguration;
import smartcampus.dto.AuthResponse;
import smartcampus.dto.PerformanceBandRequest;
import smartcampus.dto.PerformanceBandResponse;
import smartcampus.dto.PerformanceClassificationResponse;
import smartcampus.entity.PerformanceBand;
import smartcampus.entity.PerformanceCategory;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.PerformanceBandRepository;
import smartcampus.repository.UserRepository;
import smartcampus.service.PerformanceClassifier;
import tools.jackson.databind.ObjectMapper;

/**
 * The Phase 5 "configurable performance thresholds" checkpoint: the seeded {@code
 * performance_bands} rows (proving the entity mapping validates against {@code V5__analytics.sql}
 * and that nothing about the scale is hard-coded in Java), the exact {@link PerformanceClassifier}
 * classification rule including the "not enough data" edge cases (§69: never defaulted to
 * AT_RISK), the fact that changing a threshold through the real admin API changes classification
 * for the SAME figures, and the {@code PerformanceBandService#update} validation (monotonicity +
 * catch-all) together with the trap-1 regression guard: a rejected update must not be partially
 * persisted.
 *
 * <p>Exercised through the real {@code SecurityConfig} filter chain and the real {@code
 * /api/performance-bands} controller against Testcontainers MySQL with real Flyway migrations —
 * no H2, no mocking.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PerformanceBandConfigurationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PerformanceBandRepository performanceBandRepository;
    @Autowired private PerformanceClassifier performanceClassifier;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    // "PB" (Performance Bands) tags every fixture this class creates — the cached Spring
    // TestContext shares one physical database across every checkpoint test class, so a
    // generic per-class counter is not enough to guarantee uniqueness on its own (see
    // MarksAndGradesCheckpointTest's identical note).
    private static final String PREFIX = "PB";

    private static String tag() {
        return String.valueOf(SEQUENCE.incrementAndGet());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private String adminToken() throws Exception {
        String t = tag();
        String email = PREFIX.toLowerCase() + "-admin" + t + "@example.com";
        User admin =
                userRepository.save(
                        User.builder()
                                .email(email)
                                .password(passwordEncoder.encode(RAW_PASSWORD))
                                .fullName(PREFIX + " Admin " + t)
                                .role(Role.ADMIN)
                                .build());
        String body =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\"" + admin.getEmail() + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }

    private List<PerformanceBandResponse> listPerformanceBands(String token) throws Exception {
        String body =
                mockMvc.perform(get("/api/performance-bands").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(
                body,
                objectMapper.getTypeFactory().constructCollectionType(List.class, PerformanceBandResponse.class));
    }

    private void putPerformanceBand(String token, Long id, PerformanceBandRequest request) throws Exception {
        mockMvc.perform(
                        put("/api/performance-bands/" + id)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private int putPerformanceBandStatus(String token, Long id, PerformanceBandRequest request) throws Exception {
        return mockMvc
                .perform(
                        put("/api/performance-bands/" + id)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private static PerformanceBandRequest asRequest(PerformanceBandResponse band) {
        return new PerformanceBandRequest(
                band.minMarksPercentage(),
                band.minAttendancePercentage(),
                band.minGpa(),
                band.colorHex(),
                band.description());
    }

    // ------------------------------------------------------------------
    // (1) The four seeded bands load in displayOrder order with the seeded thresholds —
    // proves the entity mapping validates against V5 and nothing is hard-coded in Java.
    // ------------------------------------------------------------------

    @Test
    void seededBandsLoadInDisplayOrderWithSeededThresholds() throws Exception {
        String admin = adminToken();
        List<PerformanceBandResponse> bands = listPerformanceBands(admin);

        assertThat(bands).hasSize(4);
        assertThat(bands).extracting(PerformanceBandResponse::displayOrder).containsExactly(1, 2, 3, 4);
        assertThat(bands)
                .extracting(PerformanceBandResponse::category)
                .containsExactly(
                        PerformanceCategory.EXCELLENT,
                        PerformanceCategory.GOOD,
                        PerformanceCategory.AVERAGE,
                        PerformanceCategory.AT_RISK);

        PerformanceBandResponse excellent = bands.get(0);
        assertThat(excellent.minMarksPercentage()).isEqualByComparingTo(bd("85.00"));
        assertThat(excellent.minAttendancePercentage()).isEqualByComparingTo(bd("90.00"));
        assertThat(excellent.minGpa()).isNull();
        assertThat(excellent.colorHex()).isEqualTo("#16A34A");

        PerformanceBandResponse good = bands.get(1);
        assertThat(good.minMarksPercentage()).isEqualByComparingTo(bd("70.00"));
        assertThat(good.minAttendancePercentage()).isEqualByComparingTo(bd("80.00"));
        assertThat(good.colorHex()).isEqualTo("#2563EB");

        PerformanceBandResponse average = bands.get(2);
        assertThat(average.minMarksPercentage()).isEqualByComparingTo(bd("50.00"));
        assertThat(average.minAttendancePercentage()).isEqualByComparingTo(bd("75.00"));
        assertThat(average.colorHex()).isEqualTo("#CA8A04");

        PerformanceBandResponse atRisk = bands.get(3);
        assertThat(atRisk.minMarksPercentage()).isEqualByComparingTo(bd("0.00"));
        assertThat(atRisk.minAttendancePercentage()).isEqualByComparingTo(bd("0.00"));
        assertThat(atRisk.minGpa()).isNull();
        assertThat(atRisk.colorHex()).isEqualTo("#DC2626");
    }

    // ------------------------------------------------------------------
    // (2) classify() picks the first matching band, against the seeded thresholds.
    // ------------------------------------------------------------------

    @Test
    void classifyPicksFirstMatchingSeededBand() {
        assertThat(performanceClassifier.classify(bd("92"), bd("95"), null).category())
                .isEqualTo(PerformanceCategory.EXCELLENT);
        assertThat(performanceClassifier.classify(bd("75"), bd("85"), null).category())
                .isEqualTo(PerformanceCategory.GOOD);
        assertThat(performanceClassifier.classify(bd("55"), bd("78"), null).category())
                .isEqualTo(PerformanceCategory.AVERAGE);
        assertThat(performanceClassifier.classify(bd("20"), bd("40"), null).category())
                .isEqualTo(PerformanceCategory.AT_RISK);
    }

    // ------------------------------------------------------------------
    // (3) Missing marks and/or attendance -> null category with the exact documented
    // reason, NEVER AT_RISK (§69).
    // ------------------------------------------------------------------

    @Test
    void missingMarksOrAttendance_neverClassifiedAsAtRisk_exactReasons() {
        PerformanceClassificationResponse missingMarks = performanceClassifier.classify(null, bd("95"), null);
        assertThat(missingMarks.category()).isNull();
        assertThat(missingMarks.colorHex()).isNull();
        assertThat(missingMarks.description()).isNull();
        assertThat(missingMarks.attendancePercentage()).isEqualByComparingTo(bd("95"));
        assertThat(missingMarks.reason())
                .isEqualTo("Not enough data to classify: this student has no graded marks yet.");

        PerformanceClassificationResponse missingAttendance = performanceClassifier.classify(bd("92"), null, null);
        assertThat(missingAttendance.category()).isNull();
        assertThat(missingAttendance.marksPercentage()).isEqualByComparingTo(bd("92"));
        assertThat(missingAttendance.reason())
                .isEqualTo("Not enough data to classify: this student has no held classes yet.");

        PerformanceClassificationResponse missingBoth = performanceClassifier.classify(null, null, null);
        assertThat(missingBoth.category()).isNull();
        assertThat(missingBoth.reason())
                .isEqualTo(
                        "Not enough data to classify: this student has no graded marks and no held classes yet.");
    }

    // ------------------------------------------------------------------
    // (4) An admin PUT raising EXCELLENT's marks minimum through the real API changes
    // classification for the SAME figures on the very next call — proof the
    // classification is genuinely DB-driven, not hard-coded.
    // ------------------------------------------------------------------

    @Test
    void adminRaisingExcellentThreshold_changesClassificationForSameFigures() throws Exception {
        String admin = adminToken();
        List<PerformanceBandResponse> bands = listPerformanceBands(admin);
        PerformanceBandResponse originalExcellent =
                bands.stream().filter(b -> b.category() == PerformanceCategory.EXCELLENT).findFirst().orElseThrow();

        // 90/95 currently classifies EXCELLENT (>= 85/90).
        assertThat(performanceClassifier.classify(bd("90"), bd("95"), null).category())
                .isEqualTo(PerformanceCategory.EXCELLENT);

        try {
            PerformanceBandRequest raised =
                    new PerformanceBandRequest(
                            bd("95.00"),
                            originalExcellent.minAttendancePercentage(),
                            originalExcellent.minGpa(),
                            originalExcellent.colorHex(),
                            originalExcellent.description());
            putPerformanceBand(admin, originalExcellent.id(), raised);

            // Same figures, unchanged marks/attendance inputs, but the SAME 90/95 no
            // longer clears the now-95 EXCELLENT bar and falls through to GOOD.
            PerformanceClassificationResponse afterChange = performanceClassifier.classify(bd("90"), bd("95"), null);
            assertThat(afterChange.category()).isEqualTo(PerformanceCategory.GOOD);
        } finally {
            // Restore the shared seed data so no other test (or class run in the same
            // cached Spring context) observes the mutated threshold.
            putPerformanceBand(admin, originalExcellent.id(), asRequest(originalExcellent));
        }

        assertThat(performanceClassifier.classify(bd("90"), bd("95"), null).category())
                .isEqualTo(PerformanceCategory.EXCELLENT);
    }

    // ------------------------------------------------------------------
    // (5) update() rejects a non-monotonic set and rejects breaking the AT_RISK
    // catch-all, and — the trap-1 regression guard — the rejected values are NOT
    // persisted even partially: re-reading the row via the repository shows it
    // unchanged.
    // ------------------------------------------------------------------

    @Test
    void updateRejectsNonMonotonicAndCatchAllViolations_rejectedValuesNeverPersisted() throws Exception {
        String admin = adminToken();
        List<PerformanceBandResponse> bands = listPerformanceBands(admin);
        PerformanceBandResponse good =
                bands.stream().filter(b -> b.category() == PerformanceCategory.GOOD).findFirst().orElseThrow();
        PerformanceBandResponse atRisk =
                bands.stream().filter(b -> b.category() == PerformanceCategory.AT_RISK).findFirst().orElseThrow();

        // Non-monotonic: GOOD demanding 90% marks while EXCELLENT (stricter, tested
        // first) only demands 85% — GOOD would be unreachable.
        PerformanceBandRequest nonMonotonic =
                new PerformanceBandRequest(
                        bd("90.00"), good.minAttendancePercentage(), good.minGpa(), good.colorHex(), good.description());
        assertThat(putPerformanceBandStatus(admin, good.id(), nonMonotonic)).isEqualTo(400);

        PerformanceBand goodRow = performanceBandRepository.findById(good.id()).orElseThrow();
        assertThat(goodRow.getMinMarksPercentage()).isEqualByComparingTo(good.minMarksPercentage());
        assertThat(goodRow.getMinAttendancePercentage()).isEqualByComparingTo(good.minAttendancePercentage());

        // Catch-all violation: AT_RISK (highest displayOrder) must keep 0/0/null.
        PerformanceBandRequest breaksCatchAll =
                new PerformanceBandRequest(
                        bd("10.00"),
                        atRisk.minAttendancePercentage(),
                        atRisk.minGpa(),
                        atRisk.colorHex(),
                        atRisk.description());
        assertThat(putPerformanceBandStatus(admin, atRisk.id(), breaksCatchAll)).isEqualTo(400);

        PerformanceBand atRiskRow = performanceBandRepository.findById(atRisk.id()).orElseThrow();
        assertThat(atRiskRow.getMinMarksPercentage()).isEqualByComparingTo(bd("0.00"));
        assertThat(atRiskRow.getMinAttendancePercentage()).isEqualByComparingTo(bd("0.00"));
        assertThat(atRiskRow.getMinGpa()).isNull();

        // A GPA requirement on the catch-all is equally rejected.
        PerformanceBandRequest catchAllWithGpa =
                new PerformanceBandRequest(bd("0.00"), bd("0.00"), bd("4.00"), atRisk.colorHex(), atRisk.description());
        assertThat(putPerformanceBandStatus(admin, atRisk.id(), catchAllWithGpa)).isEqualTo(400);

        PerformanceBand atRiskRowAgain = performanceBandRepository.findById(atRisk.id()).orElseThrow();
        assertThat(atRiskRowAgain.getMinGpa()).isNull();
    }
}
