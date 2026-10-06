package kr.it.acfourd.knda_check.auth;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 개발 전용: 메일 대신 로그에 링크를 찍는다. app.dev-mode=true 일 때만 만들어진다. */
@Component
@ConditionalOnProperty(name = "app.dev-mode", havingValue = "true")
class LoggingLoginMailSender implements LoginMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingLoginMailSender.class);

    @Override
    public void sendLoginLink(String toEmail, String studentName, String link, Duration validity) {
        log.info("[개발용 메일 대체] 받는 사람: {} / 로그인 링크({}분 유효): {}", toEmail, validity.toMinutes(), link);
    }
}