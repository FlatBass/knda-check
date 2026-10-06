package kr.it.acfourd.knda_check.dashboard;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 수강생 본인의 대시보드. 학생 ID는 요청 값이 아니라 로그인 정보에서만 가져온다. */
@Controller
class StudentMyController {

    private final StudentDashboardService service;

    StudentMyController(StudentDashboardService service) {
        this.service = service;
    }

    @GetMapping("/my")
    String my(Authentication authentication, Model model) {
        model.addAttribute("view", service.load(authentication.getName()));
        model.addAttribute("showLogout", true);
        return "student/dashboard";
    }
}