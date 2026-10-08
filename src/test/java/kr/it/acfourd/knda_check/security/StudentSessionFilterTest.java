package kr.it.acfourd.knda_check.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class StudentSessionFilterTest {

    private final StudentSessionFilter filter = new StudentSessionFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpSession run(String role) throws Exception {
        var session = new MockHttpSession();
        session.setMaxInactiveInterval((int) Duration.ofHours(8).toSeconds());
        var request = new MockHttpServletRequest();
        request.setSession(session);
        if (role != null) {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("x", "n/a",
                            List.of(new SimpleGrantedAuthority(role))));
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return session;
    }

    @Test
    void 학생_세션은_3일로_늘어난다() throws Exception {
        assertThat(run("ROLE_STUDENT").getMaxInactiveInterval()).isEqualTo(3 * 24 * 3600);
    }

    @Test
    void 관리자_세션은_그대로다() throws Exception {
        assertThat(run("ROLE_ADMIN").getMaxInactiveInterval()).isEqualTo(8 * 3600);
    }

    @Test
    void 로그인하지_않은_세션은_그대로다() throws Exception {
        assertThat(run(null).getMaxInactiveInterval()).isEqualTo(8 * 3600);
    }

    @Test
    void 세션이_없으면_만들지_않는다() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("x", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        var request = new MockHttpServletRequest();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(request.getSession(false)).isNull();
    }
}