package kr.it.acfourd.knda_check.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 학생 세션만 유지 기간을 길게(3일) 잡는다. 관리자 등 다른 세션은 기본값(spring.session.timeout)을 그대로 쓴다.
 * 로그인 처리 코드와 상관없이, 인증된 ROLE_STUDENT 요청에서 세션 만료 시간을 맞춘다.
 */
public class StudentSessionFilter extends OncePerRequestFilter {

    static final Duration STUDENT_SESSION = Duration.ofDays(3);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_STUDENT".equals(a.getAuthority()))) {
            HttpSession session = request.getSession(false);
            int seconds = (int) STUDENT_SESSION.toSeconds();
            if (session != null && session.getMaxInactiveInterval() != seconds) {
                session.setMaxInactiveInterval(seconds);
            }
        }
        chain.doFilter(request, response);
    }
}