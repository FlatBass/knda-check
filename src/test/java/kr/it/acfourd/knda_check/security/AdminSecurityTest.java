package kr.it.acfourd.knda_check.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 실제 설정(config/application-local.properties)과 DB를 쓰는 테스트. 로그인 실패 테스트는 한 번만
 * 둔다(잠금 횟수를 쓰기 때문).
 */
@SpringBootTest
class AdminSecurityTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    AdminProperties admin;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void 로그인_없이_관리자_화면은_로그인_페이지로_이동한다() throws Exception {
        mvc.perform(get("/admin/attendance").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void 로그인_없이_개발용_화면도_막힌다() throws Exception {
        mvc.perform(get("/dev/students").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
    }

    @Test
    void 로그인_화면은_누구나_열_수_있다() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void 관리자_권한이_있으면_입력_화면이_열린다() throws Exception {
        mvc.perform(get("/admin/attendance").param("classCode", "PORT").param("date", "2026-08-18")
                .with(user("tester").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void 관리자가_아닌_권한은_거부된다() throws Exception {
        mvc.perform(get("/admin/attendance").with(user("tester").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRF_토큰_없는_변경_요청은_거부된다() throws Exception {
        mvc.perform(post("/admin/attendance/fill").param("classCode", "PORT").param("date", "2026-08-18")
                .with(user("tester").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 틀린_비밀번호는_로그인_실패로_돌아간다() throws Exception {
        mvc.perform(post("/login").with(csrf())
                .param("username", admin.username())
                .param("password", "this-is-not-the-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

        @Test
    void 수강생_이메일_관리_화면도_관리자만_열_수_있다() throws Exception {
        mvc.perform(get("/admin/students").with(user("P001").roles("STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/students").with(user("tester").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}