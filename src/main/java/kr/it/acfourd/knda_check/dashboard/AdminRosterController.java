package kr.it.acfourd.knda_check.dashboard;

import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.student.StudentRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자용: 수강생 목록에서 한 명을 골라 그 수강생이 보는 출결 현황 화면을 확인한다.
 * /admin/** 은 ROLE_ADMIN 만 접근할 수 있다(SecurityConfig).
 */
@Controller
@RequestMapping("/admin/roster")
public class AdminRosterController {

    private final StudentRepository students;
    private final StudentDashboardService service;

    public AdminRosterController(StudentRepository students, StudentDashboardService service) {
        this.students = students;
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("port", students.findByClassCodeOrderByNameAscIdAsc(ClassCode.PORT));
        model.addAttribute("ai", students.findByClassCodeOrderByNameAscIdAsc(ClassCode.AI));
        return "admin/roster";
    }

    @GetMapping("/{studentId}")
    public String view(@PathVariable String studentId, Model model) {
        model.addAttribute("view", service.load(studentId));
        model.addAttribute("adminView", true);
        return "student/dashboard";
    }
}