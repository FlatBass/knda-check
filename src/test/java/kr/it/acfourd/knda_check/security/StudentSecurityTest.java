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

@SpringBootTest
class StudentSecurityTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void 로그인_없이_my에_오면_학생_로그인_화면으로_이동한다() throws Exception {
        mvc.perform(get("/my").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/student/login"));
    }

    @Test
    void 학생_로그인_화면과_링크_확인_화면은_누구나_열_수_있다() throws Exception {
        mvc.perform(get("/student/login")).andExpect(status().isOk());
        mvc.perform(get("/auth/verify").param("token", "x")).andExpect(status().isOk());
    }

    @Test
    void 학생_권한으로_my가_열린다() throws Exception {
        mvc.perform(get("/my").with(user("P001").roles("STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void 관리자_권한으로는_my가_열리지_않는다() throws Exception {
        mvc.perform(get("/my").with(user("tester").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 학생_권한으로는_관리자_화면이_열리지_않는다() throws Exception {
        mvc.perform(get("/admin/attendance").with(user("P001").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 학생_권한으로는_학생_목록이_열리지_않는다() throws Exception {
        mvc.perform(get("/admin/roster").with(user("P001").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 학생_권한으로는_다른_학생_정보를_볼_수_없다() throws Exception {
        mvc.perform(get("/admin/roster/P001").with(user("P001").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 링크_요청과_링크_확인은_CSRF_토큰이_없으면_거부된다() throws Exception {
        mvc.perform(post("/student/login").param("email", "a@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/verify").param("token", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 잘못된_토큰이면_학생_로그인_화면으로_돌아간다() throws Exception {
        mvc.perform(post("/auth/verify").with(csrf()).param("token", "not-a-real-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/login?invalid"));
    }

    @Test
    void 등록되지_않은_이메일도_같은_화면으로_안내한다() throws Exception {
        mvc.perform(post("/student/login").with(csrf()).param("email", "nobody-registered@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/login?sent"));
    }
}