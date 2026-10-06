package kr.it.acfourd.knda_check.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 잠금 중에는 올바른 비밀번호여도 로그인 요청을 받지 않는다. 빈(@Component)으로 등록하지 않고 SecurityConfig에서만
 * 붙인다.
 */
class LoginLockFilter extends OncePerRequestFilter {

    private final LoginAttemptService attempts;

    LoginLockFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath()) && attempts.isLocked()) {
            response.sendRedirect(request.getContextPath() + "/login?locked");
            return;
        }
        chain.doFilter(request, response);
    }
}