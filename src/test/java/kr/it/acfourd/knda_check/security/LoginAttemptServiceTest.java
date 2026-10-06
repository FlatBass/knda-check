package kr.it.acfourd.knda_check.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    /** 시간을 마음대로 움직일 수 있는 테스트용 시계 */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-06T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    void 네_번_실패까지는_잠기지_않고_다섯_번째에_잠긴다() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure();
        }
        assertThat(service.isLocked()).isFalse();

        service.recordFailure();
        assertThat(service.isLocked()).isTrue();
    }

    @Test
    void 십분이_지나면_잠금이_풀리고_횟수도_초기화된다() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure();
        }
        clock.advance(Duration.ofMinutes(9));
        assertThat(service.isLocked()).isTrue();

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.isLocked()).isFalse();

        for (int i = 0; i < 4; i++) {
            service.recordFailure();
        }
        assertThat(service.isLocked()).isFalse();
    }

    @Test
    void 로그인에_성공하면_실패_횟수가_초기화된다() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure();
        }
        service.recordSuccess();
        service.recordFailure();

        assertThat(service.isLocked()).isFalse();
    }
}