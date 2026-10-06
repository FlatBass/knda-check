package kr.it.acfourd.knda_check.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import kr.it.acfourd.knda_check.student.StudentRepository;

/** 실제 DB에 접속하는 테스트. 메일 발송은 가짜로 바꾸고, 테스트용 수강생(T001)과 토큰은 끝날 때 롤백된다. */
@SpringBootTest
@Transactional
class StudentLoginServiceTest {

    @Autowired StudentLoginService service;
    @Autowired StudentRepository students;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean LoginMailSender mailSender;

    @BeforeEach
    void setUp() {
        jdbc.update("""
                insert into student (id, class_code, name, sick_leave_limit, email)
                values ('T001', 'PORT', '테스트', 6, 'Test.User@Example.com')
                """);
    }

    @Test
    void 등록된_이메일이면_대소문자와_관계없이_링크를_보낸다() {
        service.requestLink("  test.user@example.com ");

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendLoginLink(eq("Test.User@Example.com"), eq("테스트"), link.capture(), any());
        assertThat(link.getValue()).contains("/auth/verify?token=");
    }

    @Test
    void 등록되지_않은_이메일에는_아무것도_보내지_않는다() {
        service.requestLink("nobody@example.com");

        verifyNoInteractions(mailSender);
    }

    @Test
    void 링크는_한_번만_사용할_수_있다() {
        String token = requestAndCaptureToken();

        assertThat(service.consume(token)).contains("T001");
        assertThat(service.consume(token)).isEmpty();
    }

    @Test
    void 처음_로그인하면_이메일_인증_시각이_기록된다() {
        assertThat(students.findById("T001").orElseThrow().getEmailVerifiedAt()).isNull();

        service.consume(requestAndCaptureToken());

        assertThat(students.findById("T001").orElseThrow().getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void 만료된_링크는_사용할_수_없다() {
        String token = requestAndCaptureToken();
        jdbc.update("update email_login_token set expires_at = now() - interval '1 minute' where student_id = 'T001'");

        assertThat(service.consume(token)).isEmpty();
    }

    @Test
    void 새_링크를_요청하면_이전_링크는_무효가_된다() {
        String first = requestAndCaptureToken();
        String second = requestAndCaptureToken();

        assertThat(service.consume(first)).isEmpty();
        assertThat(service.consume(second)).contains("T001");
    }

    @Test
    void 요청이_세_번을_넘으면_더_보내지_않는다() {
        for (int i = 0; i < 5; i++) {
            service.requestLink("test.user@example.com");
        }

        verify(mailSender, times(3)).sendLoginLink(any(), any(), any(), any());
    }

    @Test
    void 링크를_보낸_뒤_이메일이_바뀌면_이전_링크는_무효가_된다() {
        String token = requestAndCaptureToken();
        jdbc.update("update student set email = 'other@example.com' where id = 'T001'");
        em.flush();
        em.clear();      // JPA가 들고 있는 이전 값을 버리고 DB에서 다시 읽게 한다

        assertThat(service.consume(token)).isEmpty();
    }

    @Test
    void 엉뚱한_토큰은_거부한다() {
        assertThat(service.consume("garbage")).isEmpty();
        assertThat(service.consume("")).isEmpty();
        assertThat(service.consume(null)).isEmpty();
    }

    // ---- 도우미 ----

    /** 링크를 요청하고, 가장 마지막에 발송된 링크에서 토큰 원문을 꺼낸다. */
    private String requestAndCaptureToken() {
        service.requestLink("test.user@example.com");
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailSender, atLeastOnce()).sendLoginLink(any(), any(), link.capture(), any());
        String last = link.getAllValues().get(link.getAllValues().size() - 1);
        return last.substring(last.indexOf("token=") + "token=".length());
    }
}