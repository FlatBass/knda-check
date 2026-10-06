package kr.it.acfourd.knda_check.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 계정 테이블 없이 설정값의 관리자 한 명만 둔다. */
    @Bean
    UserDetailsService userDetailsService(AdminProperties admin) {
        UserDetails user = User.withUsername(admin.username())
                .password(admin.passwordHash())
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

        @Bean
    SecurityFilterChain filterChain(HttpSecurity http, LoginAttemptService attempts) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/student/login", "/auth/**").permitAll()
                        .requestMatchers("/my").hasRole("STUDENT")
                        .requestMatchers("/admin/**", "/dev/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // 로그인 없이 /my 에 오면 관리자 로그인이 아니라 학생 로그인 화면으로 보낸다
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/student/login"),
                        request -> "/my".equals(request.getRequestURI().substring(request.getContextPath().length()))))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin/attendance")
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            boolean admin = authentication != null && authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                            response.sendRedirect(request.getContextPath()
                                    + (admin ? "/login?logout" : "/student/login?logout"));
                        }))
                .addFilterBefore(new LoginLockFilter(attempts), UsernamePasswordAuthenticationFilter.class);
        // CSRF 방어는 기본값(켜짐)을 그대로 사용한다. Thymeleaf의 th:action 폼에는 토큰이 자동으로 들어간다.
        return http.build();
    }
}