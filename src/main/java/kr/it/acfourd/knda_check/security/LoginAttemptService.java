package kr.it.acfourd.knda_check.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 로그인 실패 제한. 관리자가 한 명이라 계정 단위가 아니라 전체 단위로 센다.
 * 5회 연속 실패하면 10분간 로그인을 받지 않는다 (공격자가 잠글 수는 있지만 들어올 수는 없다).
 * 메모리에만 저장하므로 서버 인스턴스마다 따로 센다.
 */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final Clock clock;
    private int failures;
    private Instant lockedUntil;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean isLocked() {
        if (lockedUntil == null) {
            return false;
        }
        if (!clock.instant().isBefore(lockedUntil)) {
            lockedUntil = null;
            failures = 0;
            return false;
        }
        return true;
    }

    public synchronized void recordFailure() {
        if (isLocked()) {
            return;
        }
        failures++;
        if (failures >= MAX_FAILURES) {
            lockedUntil = clock.instant().plus(LOCK_DURATION);
        }
    }

    public synchronized void recordSuccess() {
        failures = 0;
        lockedUntil = null;
    }
}