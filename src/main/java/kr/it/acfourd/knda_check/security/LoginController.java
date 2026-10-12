package kr.it.acfourd.knda_check.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LoginController {

    @GetMapping("/login")
    String login() {
        return "login";
    }

    /** 기본 주소로 들어오면 역할에 맞는 첫 화면으로 보낸다. */
    @GetMapping("/")
    String home(Authentication auth) {
        if (hasRole(auth, "ROLE_ADMIN")) {
            return "redirect:/admin/attendance";
        }
        if (hasRole(auth, "ROLE_STUDENT")) {
            return "redirect:/my";
        }
        return "redirect:/student/login";
    }

    private static boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }
}