package kr.it.acfourd.knda_check.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/students")
class AdminStudentController {

    private static final int MAX_PASTE_LENGTH = 20_000;
    private static final String HOME = "redirect:/admin/students";

    private final StudentEmailService service;

    AdminStudentController(StudentEmailService service) {
        this.service = service;
    }

    @GetMapping
    String view(Model model) {
        model.addAttribute("rows", service.list());
        return "admin/students";
    }

    @PostMapping("/paste")
    String paste(@RequestParam String text, RedirectAttributes redirect) {
        if (text.length() > MAX_PASTE_LENGTH) {
            redirect.addFlashAttribute("errors", List.of("붙여넣은 내용이 너무 깁니다"));
            return HOME;
        }
        StudentEmailService.ParseResult parsed = StudentEmailService.parsePaste(text);
        if (!parsed.errors().isEmpty()) {
            redirect.addFlashAttribute("errors", parsed.errors());
            return HOME;
        }
        if (parsed.entries().isEmpty()) {
            redirect.addFlashAttribute("errors", List.of("읽을 수 있는 줄이 없습니다"));
            return HOME;
        }
        return applyAndRedirect(parsed.entries(), redirect);
    }

    /** 표의 모든 행을 받아서, 실제로 바뀐 행만 저장한다. */
    @PostMapping("/save")
    String save(@RequestParam MultiValueMap<String, String> params, RedirectAttributes redirect) {
        List<String> ids = params.get("studentId");
        if (ids == null || ids.isEmpty()) {
            redirect.addFlashAttribute("errors", List.of("저장할 수강생이 없습니다"));
            return HOME;
        }
        Map<String, String> requested = new LinkedHashMap<>();
        for (String id : ids) {
            requested.put(id, params.getFirst("email_" + id));
        }
        return applyAndRedirect(requested, redirect);
    }

    private String applyAndRedirect(Map<String, String> requested, RedirectAttributes redirect) {
        try {
            int changed = service.apply(requested);
            redirect.addFlashAttribute("message",
                    changed == 0 ? "바뀐 내용이 없습니다." : changed + "명의 이메일을 저장했습니다.");
        } catch (EmailUpdateException e) {
            redirect.addFlashAttribute("errors", e.getErrors());
        }
        return HOME;
    }
}