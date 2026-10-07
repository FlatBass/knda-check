package kr.it.acfourd.knda_check.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class ResendLoginMailSenderTest {

    @Test
    void 올바른_형식으로_Resend에_요청한다() throws Exception {
        var path = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var ua = new AtomicReference<String>();
        var ctype = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            path.set(ex.getRequestMethod() + " " + ex.getRequestURI().getPath());
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            ua.set(ex.getRequestHeaders().getFirst("User-Agent"));
            ctype.set(ex.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"id\":\"abc\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, ok.length);
            ex.getResponseBody().write(ok);
            ex.close();
        });
        server.start();
        try {
            var sender = new ResendLoginMailSender("re_test", "knda-check <no-reply@check.acfourd.it.kr>",
                    "http://127.0.0.1:" + server.getAddress().getPort());
            sender.sendLoginLink("a@example.com", "홍<길>동", "https://x.test/auth/verify?token=abc", Duration.ofMinutes(10));
        } finally {
            server.stop(0);
        }
        assertThat(path.get()).isEqualTo("POST /emails");
        assertThat(auth.get()).isEqualTo("Bearer re_test");
        assertThat(ua.get()).isEqualTo("knda-check");
        assertThat(ctype.get()).startsWith("application/json");
        assertThat(body.get()).contains("\"to\":[\"a@example.com\"]")
                .contains("no-reply@check.acfourd.it.kr")
                .contains("10분")
                .contains("https://x.test/auth/verify?token=abc")
                .contains("홍&lt;길&gt;동");
    }

    @Test
    void 발송이_실패해도_예외를_던지지_않는다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> { ex.sendResponseHeaders(403, -1); ex.close(); });
        server.start();
        try {
            var sender = new ResendLoginMailSender("re_test", "a@b.c", "http://127.0.0.1:" + server.getAddress().getPort());
            assertThatCode(() -> sender.sendLoginLink("a@example.com", "홍길동", "https://x.test", Duration.ofMinutes(10)))
                    .doesNotThrowAnyException();
        } finally {
            server.stop(0);
        }
        var dead = new ResendLoginMailSender("re_test", "a@b.c", "http://127.0.0.1:1");
        assertThatCode(() -> dead.sendLoginLink("a@example.com", "홍길동", "https://x.test", Duration.ofMinutes(10)))
                .doesNotThrowAnyException();
    }
}