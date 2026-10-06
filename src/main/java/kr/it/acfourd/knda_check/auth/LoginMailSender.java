package kr.it.acfourd.knda_check.auth;

import java.time.Duration;

/** 로그인 링크 메일 발송. 지금은 개발용 로그 출력만 있고, 나중에 Resend 구현을 붙인다. */
public interface LoginMailSender {

    void sendLoginLink(String toEmail, String studentName, String link, Duration validity);
}