package kr.it.acfourd.knda_check.dashboard;

import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.student.StudentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 개발 전용: 인증이 없는 동안 학생을 직접 골라 대시보드를 확인한다.
 * app.dev-mode=true 일 때만 만들어지며, 인증을 넣을 때 삭제하고 /my 로 대체한다.
 */
@Controller
@ConditionalOnProperty(name = "app.dev-mode", havingValue = "true")
@RequestMapping("/dev")
public class DevDashboardController {

    private final StudentRepository students;
    private final StudentDashboardService service;

    public DevDashboardController(StudentRepository students, StudentDashboardService service) {
        this.students = students;
        this.service = service;
    }

    @GetMapping("/students")
    public String students(Model model) {
        model.addAttribute("port", students.findByClassCodeOrderById(ClassCode.PORT));
        model.addAttribute("ai", students.findByClassCodeOrderById(ClassCode.AI));
        return "dev/students";
    }

    @GetMapping("/my")
    public String my(@RequestParam String studentId, Model model) {
        model.addAttribute("view", service.load(studentId));
        return "student/dashboard";
    }
}