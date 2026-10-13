package kr.it.acfourd.knda_check.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import kr.it.acfourd.knda_check.attendance.Attendance;
import kr.it.acfourd.knda_check.attendance.AttendanceRepository;
import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.schedule.TrainingDay;
import kr.it.acfourd.knda_check.schedule.UnitPeriod;
import kr.it.acfourd.knda_check.schedule.UnitPeriodRepository;
import kr.it.acfourd.knda_check.student.Student;
import kr.it.acfourd.knda_check.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** 실제 계산 서비스의 DTO를 렌더링한다. 저장소만 대체하므로 DB 연결은 필요하지 않다. */
class StudentDashboardTemplateTest {
    private final Student student = mock(Student.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final UnitPeriodRepository periods = mock(UnitPeriodRepository.class);
    private final AttendanceRepository attendances = mock(AttendanceRepository.class);
    private final StudentDashboardService service = new StudentDashboardService(students, periods, attendances);
    private final SpringTemplateEngine templates = new SpringTemplateEngine();

    @BeforeEach
    void setUp() {
        when(student.getId()).thenReturn("T001");
        when(student.getName()).thenReturn("테스트");
        when(student.getClassCode()).thenReturn(ClassCode.PORT);
        when(student.getEnrollmentStatus()).thenReturn("수강중");
        when(student.getSickLeaveLimit()).thenReturn(6);
        when(students.findById("T001")).thenReturn(Optional.of(student));

        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        templates.setTemplateResolver(resolver);
    }

    @ParameterizedTest
    @CsvSource({"0,3,0,0,3,1", "2,2,0,1,4,1", "1,0,0,1,1,0", "1,1,1,0,3,1", "0,0,0,0,0,0"})
    void 상단은_미환산과_환산대상누적_하단은_개별발생이력과_환산결과(int late, int early, int outing,
            int remaining, int cumulative, int converted) {
        var period = period(2);
        var view = load(List.of(period), rows(period, late, early, outing));
        String html = render(view);
        for (String title : List.of("2차 단위기간 진행 현황", "전체 과정 진행 현황")) {
            String card = card(html, title);
            String top = card.substring(0, card.indexOf("class=\"input-progress\""));
            String bottom = card.substring(card.indexOf("class=\"meta\""));

            assertThat(text(top)).contains("지각·조퇴·외출미환산" + remaining + "회/누적" + cumulative + "회");
            assertThat(top).doesNotContain("지각 누적", "조퇴 누적", "외출 누적", "class=\"event-summary\"");
            assertThat(text(bottom)).contains("지각누적" + late + "회조퇴누적" + early + "회외출누적" + outing + "회");
            assertThat(text(bottom)).contains("지각·조퇴·외출누적" + cumulative + "회→결석" + converted + "일환산");
            assertThat(text(top)).contains("17일중출석" + (12 - converted) + "일결석" + (2 + converted) + "일");
            assertThat(card).contains("width:" + view.current().stats().creditedBarPercent() + "%",
                    "width:" + view.current().stats().absentBarPercent() + "%");
        }
        assertThat(view.current().stats().extraAbsences()).isEqualTo(converted);
    }

    @Test
    void 조퇴3회의_출석률과_진행현황_기준안내는_기존결과를_유지한다() {
        var period = period(2);
        var view = load(List.of(period), rows(period, 0, 3, 0));
        String card = card(render(view), "2차 단위기간 진행 현황");

        assertThat(text(card)).contains("소정훈련일기준출석률64.71%(참고)", "출결입력현황·14/17일확정",
                "남은3일중3일이상출석해야합니다.");
        assertThat(view.current().stats().ratePercent()).isEqualTo("78.57%");
        assertThat(view.current().stats().meetsThreshold()).isFalse();
        assertThat(view.current().stats().finalCreditedDays()).isEqualTo(11);
        assertThat(view.current().stats().totalAbsences()).isEqualTo(3);
        assertThat(view.sickLeave().used()).isZero();
        assertThat(view.sickLeave().remaining()).isEqualTo(6);
    }

    @Test
    void 결석일의_지각조퇴는_상단환산대상에서_제외하고_하단발생이력에는_보존한다() {
        var period = period(2);
        var rows = rows(period, 0, 3, 0);
        rows.get(12).apply(false, false, 9, 8, 0, false, true, null, null);
        var view = load(List.of(period), rows);
        String card = card(render(view), "2차 단위기간 진행 현황");

        assertThat(text(card)).contains("지각·조퇴·외출미환산0회/누적3회",
                "지각누적9회조퇴누적11회외출누적0회", "지각·조퇴·외출누적3회→결석1일환산",
                "17일중출석11일결석3일");
        assertThat(view.current().stats().convertibleEvents()).isEqualTo(3);
        assertThat(view.current().stats().extraAbsences()).isEqualTo(1);
    }

    @Test
    void 출석인정결석일의_이력도_환산대상에서_제외하고_병결계산을_유지한다() {
        var period = period(2);
        var rows = rows(period, 0, 3, 0);
        rows.get(11).apply(false, true, 2, 1, 0, true, true, null, null);
        var view = load(List.of(period), rows);
        String card = card(render(view), "2차 단위기간 진행 현황");

        assertThat(text(card)).contains("지각·조퇴·외출미환산0회/누적3회",
                "지각누적2회조퇴누적4회외출누적0회", "17일중출석11일결석3일");
        assertThat(view.sickLeave().used()).isEqualTo(1);
        assertThat(view.sickLeave().remaining()).isEqualTo(5);
    }

    @Test
    void 단위기간의_표시와_전체기간의_재환산_결과를_분리한다() {
        var first = period(1);
        var second = period(2);
        var rows = rows(first, 2, 0, 0);
        rows.addAll(rows(second, 0, 2, 0));
        var view = load(List.of(first, second), rows);
        String html = render(view);
        String unit = card(html, "2차 단위기간 진행 현황");
        String overall = card(html, "전체 과정 진행 현황");

        assertThat(text(unit)).contains("지각·조퇴·외출미환산2회/누적2회", "지각누적0회조퇴누적2회외출누적0회",
                "지각·조퇴·외출누적2회→결석0일환산");
        assertThat(view.periods()).allSatisfy(p -> assertThat(p.stats().extraAbsences()).isZero());
        assertThat(view.overall().convertibleEvents()).isEqualTo(4);
        assertThat(view.overall().extraAbsences()).isEqualTo(1);
        assertThat(text(overall)).contains("지각·조퇴·외출미환산1회/누적4회", "지각누적2회조퇴누적2회외출누적0회",
                "지각·조퇴·외출누적4회→결석1일환산");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void 현재단위기간이_바뀌어도_동일한_표시규칙을_적용한다(int number) {
        var period = period(number);
        var view = load(List.of(period), rows(period, 2, 2, 0));
        String card = card(render(view), number + "차 단위기간 진행 현황");
        assertThat(text(card)).contains("지각·조퇴·외출미환산1회/누적4회", "지각누적2회조퇴누적2회외출누적0회");
    }

    @Test
    void 확정된_출결이_없으면_기존_빈화면을_표시한다() {
        var view = load(List.of(period(2)), List.of());
        String html = render(view);
        assertThat(html).contains("아직 확정된 출결이 없습니다.").doesNotContain("class=\"event-summary conversion-summary\"");
    }

    UnitPeriod period(int number) {
        var period = mock(UnitPeriod.class);
        var start = LocalDate.of(2026, 8, 1).plusMonths(number - 1);
        when(period.getId()).thenReturn("항만0" + number);
        when(period.getPeriodNo()).thenReturn(number);
        when(period.getStartDate()).thenReturn(start);
        when(period.getEndDate()).thenReturn(start.plusDays(27));
        when(period.getRequiredDays()).thenReturn(17);
        return period;
    }

    ArrayList<Attendance> rows(UnitPeriod period, int late, int early, int outing) {
        var rows = new ArrayList<Attendance>();
        for (int i = 0; i < 14; i++) {
            var date = period.getStartDate().plusDays(i);
            var day = mock(TrainingDay.class);
            when(day.getTrainingDate()).thenReturn(date);
            when(day.getUnitPeriod()).thenReturn(period);
            when(day.isTrainingDay()).thenReturn(true);
            var row = Attendance.create(student, day);
            row.apply(i < 12, false, i < late ? 1 : 0,
                    i >= late && i < late + early ? 1 : 0,
                    i >= late + early && i < late + early + outing ? 1 : 0,
                    false, true, null, null);
            rows.add(row);
        }
        return rows;
    }

    DashboardView load(List<UnitPeriod> unitPeriods, List<Attendance> rows) {
        when(periods.findByClassCodeOrderByPeriodNo(ClassCode.PORT)).thenReturn(unitPeriods);
        when(attendances.findAllByStudentId("T001")).thenReturn(rows);
        return service.load("T001");
    }

    String render(DashboardView view) {
        var context = new Context(Locale.KOREAN);
        context.setVariable("view", view);
        context.setVariable("showLogout", false);
        return templates.process("student/dashboard", context);
    }

    private static String card(String html, String title) {
        int start = html.lastIndexOf("<section", html.indexOf(title + "</h2>"));
        return html.substring(start, html.indexOf("</section>", start) + "</section>".length());
    }

    private static String text(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s+", "");
    }
}
