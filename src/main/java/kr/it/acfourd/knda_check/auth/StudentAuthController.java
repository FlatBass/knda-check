package kr.it.acfourd.knda_check.auth;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class StudentAuthController {

    private final StudentLoginService service;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    StudentAuthController(StudentLoginService service) {
        this.service = service;
    }

    @GetMapping("/student/login")
    String loginPage() {
        return "student/login";
    }

    /** 등록 여부와 상관없이 항상 같은 화면으로 보낸다. */
    @PostMapping("/student/login")
    String requestLink(@RequestParam String email) {
        service.requestLink(email);
        return "redirect:/student/login?sent";
    }

    /** 링크를 열어도 여기서는 토큰을 쓰지 않는다. (메일 보안 프로그램이 링크를 미리 열어도 안전) */
    @GetMapping("/auth/verify")
    String verifyPage(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token == null ? "" : token);
        return "student/verify";
    }

    @PostMapping("/auth/verify")
    String verify(@RequestParam String token, HttpServletRequest request, HttpServletResponse response) {
        Optional<String> studentId = service.consume(token);
        if (studentId.isEmpty()) {
            return "redirect:/student/login?invalid";
        }
        signIn(studentId.get(), request, response);
        return "redirect:/my";
    }

    private void signIn(String studentId, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();                       // 세션 고정 공격 방지
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                studentId, null, AuthorityUtils.createAuthorityList("ROLE_STUDENT")));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}