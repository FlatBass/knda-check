package kr.it.acfourd.knda_check.auth;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 메일 발송이 아직 연결되지 않은 운영 환경용 임시 발송기.
 * 링크와 이메일 주소는 로그에 남기지 않는다.
 * 개발 모드(app.dev-mode=true)에서는 LoggingLoginMailSender가 대신 쓰인다.
 */
@Component
@ConditionalOnExpression("!'${app.dev-mode:false}'.equals('true') and '${app.mail.resend-api-key:}'.isBlank()")
public class UnconfiguredLoginMailSender implements LoginMailSender {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredLoginMailSender.class);

    @Override
    public void sendLoginLink(String toEmail, String studentName, String link, Duration validity) {
        log.warn("로그인 링크를 보내지 못했습니다: 메일 발송이 설정되지 않았습니다.");
    }
}