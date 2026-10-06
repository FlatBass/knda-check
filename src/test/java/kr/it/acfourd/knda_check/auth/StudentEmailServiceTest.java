package kr.it.acfourd.knda_check.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import kr.it.acfourd.knda_check.student.StudentRepository;

/** 실제 DB 테스트. 테스트용 수강생(T001, T002)과 토큰은 끝날 때 롤백된다. */
@SpringBootTest
@Transactional
class StudentEmailServiceTest {

    @Autowired StudentEmailService service;
    @Autowired StudentLoginService loginService;
    @Autowired StudentRepository students;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean LoginMailSender mailSender;

    @BeforeEach
    void setUp() {
        jdbc.update("""
                insert into student (id, class_code, name, sick_leave_limit, email)
                values ('T001', 'PORT', '테스트일', 6, 'old.one@example.com'),
                       ('T002', 'PORT', '테스트이', 6, null)
                """);
    }

    @Test
    void 새_이메일을_등록하고_지울_수_있다() {
        assertThat(service.apply(Map.of("T002", "  new.two@example.com "))).isEqualTo(1);
        assertThat(emailOf("T002")).isEqualTo("new.two@example.com");

        assertThat(service.apply(Map.of("T002", ""))).isEqualTo(1);
        assertThat(emailOf("T002")).isNull();
    }

    @Test
    void 바뀌지_않은_행은_변경으로_세지_않는다() {
        var same = new LinkedHashMap<String, String>();
        same.put("T001", "old.one@example.com");
        same.put("T002", "");

        assertThat(service.apply(same)).isZero();
    }

    @Test
    void 이메일을_바꾸면_인증_이력과_쓰지_않은_링크가_초기화된다() {
        jdbc.update("update student set email_verified_at = now() where id = 'T001'");
        loginService.requestLink("old.one@example.com");
        assertThat(openTokens("T001")).isEqualTo(1);

        service.apply(Map.of("T001", "changed.one@example.com"));

        assertThat(jdbc.queryForObject("select email_verified_at from student where id = 'T001'", java.sql.Timestamp.class))
                .isNull();
        assertThat(openTokens("T001")).isZero();
    }

    @Test
    void 같은_이메일은_대소문자와_관계없이_두_명에게_줄_수_없다() {
        assertThatThrownBy(() -> service.apply(Map.of("T002", "OLD.one@example.com")))
                .isInstanceOf(EmailUpdateException.class);

        assertThat(emailOf("T002")).isNull();
    }

    @Test
    void 서로_이메일을_맞바꿀_수_있다() {
        jdbc.update("update student set email = 'old.two@example.com' where id = 'T002'");
        var swap = new LinkedHashMap<String, String>();
        swap.put("T001", "old.two@example.com");
        swap.put("T002", "old.one@example.com");

        assertThat(service.apply(swap)).isEqualTo(2);

        assertThat(emailOf("T001")).isEqualTo("old.two@example.com");
        assertThat(emailOf("T002")).isEqualTo("old.one@example.com");
    }

    @Test
    void 문제가_하나라도_있으면_전체를_저장하지_않고_문제를_모두_알려준다() {
        var bad = new LinkedHashMap<String, String>();
        bad.put("T002", "new.two@example.com");     // 이것은 정상이지만 함께 저장되면 안 된다
        bad.put("T001", "not-an-email");
        bad.put("T999", "x@example.com");

        assertThatThrownBy(() -> service.apply(bad))
                .isInstanceOfSatisfying(EmailUpdateException.class,
                        e -> assertThat(e.getErrors()).hasSize(2));

        assertThat(emailOf("T002")).isNull();
        assertThat(emailOf("T001")).isEqualTo("old.one@example.com");
    }

    private String emailOf(String id) {
        return jdbc.queryForObject("select email from student where id = ?", String.class, id);
    }

    private int openTokens(String studentId) {
        return jdbc.queryForObject(
                "select count(*) from email_login_token where student_id = ? and used_at is null", Integer.class, studentId);
    }
}