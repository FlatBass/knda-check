package kr.it.acfourd.knda_check.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
class LoginAttemptListener {

    private final LoginAttemptService attempts;

    LoginAttemptListener(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @EventListener
    void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        attempts.recordFailure();
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        attempts.recordSuccess();
    }
}