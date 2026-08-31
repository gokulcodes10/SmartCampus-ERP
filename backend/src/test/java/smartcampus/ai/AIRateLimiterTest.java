package smartcampus.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import smartcampus.entity.User;
import smartcampus.exception.AIRateLimitExceededException;
import smartcampus.repository.AIRequestLogRepository;
import smartcampus.service.AIRateLimitSnapshot;
import smartcampus.service.AIRateLimiter;

/**
 * Unit tests for {@link AIRateLimiter} against a Mockito-backed {@link
 * AIRequestLogRepository} and a fixed {@link Clock} — no Spring context, no sleeping.
 */
@ExtendWith(MockitoExtension.class)
class AIRateLimiterTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-28T10:15:30Z");

    @Mock private AIRequestLogRepository aiRequestLogRepository;

    private Clock clock;
    private AIRateLimiter rateLimiter;
    private User user;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_INSTANT, ZONE);
        rateLimiter = new AIRateLimiter(aiRequestLogRepository, clock);
        ReflectionTestUtils.setField(rateLimiter, "perMinute", 5);
        ReflectionTestUtils.setField(rateLimiter, "perDay", 100);
        user = User.builder().id(42L).build();
    }

    @Test
    void underBothLimitsPasses() {
        when(aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(eq(42L), any())).thenReturn(2L);

        rateLimiter.checkAllowed(user);
    }

    @Test
    void atTheMinuteLimitThrowsWithTheMinuteMessage() {
        LocalDateTime now = LocalDateTime.now(clock);
        when(aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(42L, now.minusMinutes(1))).thenReturn(5L);

        assertThatThrownBy(() -> rateLimiter.checkAllowed(user))
                .isInstanceOf(AIRateLimitExceededException.class)
                .hasMessageContaining("5")
                .hasMessageContaining("per minute");

        verify(aiRequestLogRepository, org.mockito.Mockito.never())
                .countByUserIdAndCreatedAtGreaterThanEqual(eq(42L), eq(now.minusDays(1)));
    }

    @Test
    void atTheDayLimitThrowsWithTheDayMessage() {
        LocalDateTime now = LocalDateTime.now(clock);
        when(aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(42L, now.minusMinutes(1))).thenReturn(1L);
        when(aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(42L, now.minusDays(1))).thenReturn(100L);

        assertThatThrownBy(() -> rateLimiter.checkAllowed(user))
                .isInstanceOf(AIRateLimitExceededException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("per day");
    }

    @Test
    void theMinuteWindowIsCheckedBeforeTheDayWindow() {
        LocalDateTime now = LocalDateTime.now(clock);
        // Both windows are over the limit; only the minute check must ever run.
        when(aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(42L, now.minusMinutes(1))).thenReturn(5L);

        assertThatThrownBy(() -> rateLimiter.checkAllowed(user)).hasMessageContaining("per minute");

        verify(aiRequestLogRepository, org.mockito.Mockito.never())
                .countByUserIdAndCreatedAtGreaterThanEqual(eq(42L), eq(now.minusDays(1)));
    }

    @Test
    void snapshotNeverThrowsAndFloorsRemainingAtZero() {
        LocalDateTime now = LocalDateTime.now(clock);
        when(aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(42L, now.minusMinutes(1))).thenReturn(9L);
        when(aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(42L, now.minusDays(1))).thenReturn(250L);

        AIRateLimitSnapshot snapshot = rateLimiter.snapshot(user);

        assertThat(snapshot.perMinute()).isEqualTo(5);
        assertThat(snapshot.perDay()).isEqualTo(100);
        assertThat(snapshot.usedLastMinute()).isEqualTo(9L);
        assertThat(snapshot.usedToday()).isEqualTo(250L);
        assertThat(snapshot.remainingMinute()).isZero();
        assertThat(snapshot.remainingDay()).isZero();
    }

    @Test
    void snapshotSincesAreExactlyOneMinuteAndOneDayBeforeTheInjectedClock() {
        when(aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(0L);

        rateLimiter.snapshot(user);

        LocalDateTime expectedNow = LocalDateTime.now(clock);
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(aiRequestLogRepository, org.mockito.Mockito.times(2))
                .countByUserIdAndCreatedAtGreaterThanEqual(eq(42L), sinceCaptor.capture());

        assertThat(sinceCaptor.getAllValues()).containsExactly(expectedNow.minusMinutes(1), expectedNow.minusDays(1));
    }
}
