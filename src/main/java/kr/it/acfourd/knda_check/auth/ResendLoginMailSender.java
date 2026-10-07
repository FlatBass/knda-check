package kr.it.acfourd.knda_check.auth;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

/**
 * Resend(https://resend.com)로 학생 로그인 링크 메일을 보낸다.
 * 개발 모드가 아니고 API 키가 설정되어 있을 때만 사용된다.
 * 발송에 실패해도 예외를 던지지 않는다(등록 여부에 따라 화면이 달라지지 않게).
 * 로그에는 이메일 주소와 링크를 남기지 않는다.
 */
@Component
@ConditionalOnExpression("!'${app.dev-mode:false}'.equals('true') and !'${app.mail.resend-api-key:}'.isBlank()")
public class ResendLoginMailSender implements LoginMailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendLoginMailSender.class);
    private static final String SUBJECT = "[K-뉴딜 아카데미] 출결 확인 로그인 링크";

    private final RestClient client;
    private final String from;

    public ResendLoginMailSender(
            @Value("${app.mail.resend-api-key}") String apiKey,
            @Value("${app.mail.from}") String from,
            @Value("${app.mail.resend-base-url:https://api.resend.com}") String baseUrl) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.USER_AGENT, "knda-check")
                .build();
        this.from = from;
    }

    @Override
    public void sendLoginLink(String toEmail, String studentName, String link, Duration validity) {
        long minutes = validity.toMinutes();
        String text = studentName + "님, 안녕하세요.\n\n"
                + "아래 링크를 누르면 출결 현황을 볼 수 있습니다. 링크는 " + minutes + "분 동안, 한 번만 사용할 수 있습니다.\n\n"
                + link + "\n\n"
                + "본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.";
        String html = "<p>" + HtmlUtils.htmlEscape(studentName) + "님, 안녕하세요.</p>"
                + "<p>아래 버튼을 누르면 출결 현황을 볼 수 있습니다. 링크는 " + minutes + "분 동안, 한 번만 사용할 수 있습니다.</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">출결 현황 보기</a></p>"
                + "<p style=\"color:#666\">본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.</p>";

        Map<String, Object> body = Map.of(
                "from", from,
                "to", List.of(toEmail),
                "subject", SUBJECT,
                "text", text,
                "html", html);
        try {
            client.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("로그인 메일 발송 실패: Resend 응답 {}", e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("로그인 메일 발송 실패: {}", e.getClass().getSimpleName());
        }
    }
}