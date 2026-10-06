package kr.it.acfourd.knda_check.attendance;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kr.it.acfourd.knda_check.common.ClassCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/attendance")
public class AdminAttendanceController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN);

    private final AdminAttendanceService service;

    public AdminAttendanceController(AdminAttendanceService service) {
        this.service = service;
    }

    /** 입력 화면. 데이터를 바꾸지 않는다. 날짜가 없으면 오늘 이전의 가장 가까운 훈련일로 이동. */
    @GetMapping
    public String view(@RequestParam(defaultValue = "PORT") ClassCode classCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model, RedirectAttributes redirect) {
        if (date == null) {
            return redirectTo(classCode, service.nearestTrainingDay(classCode, LocalDate.now(SEOUL)));
        }
        if (!service.isTrainingDay(classCode, date)) {
            LocalDate nearest = service.nearestTrainingDay(classCode, date);
            redirect.addFlashAttribute("error", date + "은(는) 훈련일이 아니어서 " + nearest + "(으)로 이동했습니다.");
            return redirectTo(classCode, nearest);
        }

        DayEntry entry = service.loadDay(classCode, date);
        AdminAttendanceService.DayNav nav = service.navigate(classCode, date);

        // 날짜는 화면에 쓸 문자열로 미리 바꿔 둔다 (링크 파라미터에서 형식이 달라지는 것을 막기 위해)
        model.addAttribute("entry", entry);
        model.addAttribute("dateIso", date.toString());
        model.addAttribute("dateLabel", date.format(LABEL));
        model.addAttribute("prevIso", nav.prev() == null ? null : nav.prev().toString());
        model.addAttribute("nextIso", nav.next() == null ? null : nav.next().toString());
        model.addAttribute("total", entry.rows().size());
        model.addAttribute("confirmedCount", entry.rows().stream().filter(DayEntry.Row::confirmed).count());
        return "admin/attendance";
    }

    @PostMapping("/fill")
    public String fill(@RequestParam ClassCode classCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            RedirectAttributes redirect) {
        try {
            int changed = service.fillAllPresent(classCode, date);
            redirect.addFlashAttribute("message", changed + "명을 출석으로 채웠습니다. 아직 확정 전입니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return redirectTo(classCode, date);
    }

    @PostMapping("/save")
    public String save(@RequestParam ClassCode classCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "save") String action,
            @RequestParam MultiValueMap<String, String> params,
            RedirectAttributes redirect) {
        try {
            service.saveDay(classCode, date, parseRows(params));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "저장하지 못했습니다: " + e.getMessage());
            return redirectTo(classCode, date);
        }

        redirect.addFlashAttribute("message", "저장했습니다.");
        LocalDate target = date;
        if ("saveNext".equals(action)) {
            LocalDate next = service.navigate(classCode, date).next();
            if (next != null) {
                target = next;
            }
        }
        return redirectTo(classCode, target);
    }

    /** 폼 필드 이름: credit_P001, late_P001 처럼 필드명_수강생ID. 체크박스는 체크되었을 때만 전송된다. */
    private List<RowInput> parseRows(MultiValueMap<String, String> p) {
        List<String> ids = p.get("studentId");
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("입력할 수강생이 없습니다");
        }
        List<RowInput> rows = new ArrayList<>();
        for (String id : ids) {
            rows.add(new RowInput(id,
                    p.containsKey("credit_" + id),
                    p.containsKey("excused_" + id),
                    number(p, "late_" + id),
                    number(p, "early_" + id),
                    number(p, "outing_" + id),
                    p.containsKey("sick_" + id),
                    p.containsKey("confirmed_" + id),
                    p.getFirst("publicNote_" + id),
                    p.getFirst("internalNote_" + id)));
        }
        return rows;
    }

    private int number(MultiValueMap<String, String> p, String key) {
        String v = p.getFirst(key);
        return (v == null || v.isBlank()) ? 0 : Integer.parseInt(v.trim());
    }

    private String redirectTo(ClassCode classCode, LocalDate date) {
        return "redirect:/admin/attendance?classCode=" + classCode.name() + "&date=" + date;
    }
}