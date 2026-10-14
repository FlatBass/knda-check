package kr.it.acfourd.knda_check.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import kr.it.acfourd.knda_check.attendance.DailyAttendance;
import kr.it.acfourd.knda_check.dashboard.DashboardView.CalendarView;
import kr.it.acfourd.knda_check.dashboard.DashboardView.CellView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AttendanceCalendarTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    @ParameterizedTest
    @CsvSource({
            "true,false,false,true,present,○,출석",
            "false,false,false,true,absent,X,결석",
            "false,true,false,true,excused,공,출석 인정 결석",
            "false,true,true,true,excused,병,인정 병결",
            "false,false,false,false,pending,·,아직 반영되지 않음"
    })
    void 출결상태를_날짜와_기호로_표시한다(boolean credit, boolean excused, boolean sick, boolean confirmed,
            String kind, String symbol, String label) {
        var row = new DailyAttendance(MONDAY, credit, excused, 0, 0, 0, sick, confirmed);
        var calendar = build(MONDAY, MONDAY.plusDays(4), List.of(row));
        assertThat(calendar.weeks().get(0).get(0))
                .isEqualTo(new CellView(kind, "9/21", symbol, "9월 21일 " + label));
        assertThat(calendar.periodId()).isEqualTo("기간02");
        assertThat(calendar.title()).isEqualTo("2차 · 9/21 ~ 9/25");
        assertThat(calendar.current()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "false,false,false,○", "true,false,false,◎", "false,true,false,▲", "false,false,true,◇",
            "true,true,false,⊙", "true,false,true,▶", "false,true,true,☆", "true,true,true,▣"
    })
    void 발생항목_여덟조합을_LMS_기호로_표시한다(boolean late, boolean early, boolean outing, String symbol) {
        assertThat(AttendanceCalendar.eventSymbol(late, early, outing)).isEqualTo(symbol);
    }

    @Test
    void 결석일은_지각조퇴외출_이력이_있어도_X만_표시한다() {
        var row = new DailyAttendance(MONDAY, false, false, 2, 1, 1, false, true);
        var cell = build(MONDAY, MONDAY, List.of(row)).weeks().get(0).get(0);
        assertThat(cell).isEqualTo(new CellView("absent", "9/21", "X", "9월 21일 결석"));
    }

    @Test
    void 미확정은_출석이나_인정병결_입력이_있어도_미반영으로_표시한다() {
        var row = new DailyAttendance(MONDAY, false, true, 1, 0, 0, true, false);
        assertThat(build(MONDAY, MONDAY, List.of(row)).weeks().get(0).get(0))
                .isEqualTo(new CellView("pending", "9/21", "·", "9월 21일 아직 반영되지 않음"));
    }

    @Test
    void 지각횟수는_기호를_반복하지_않고_설명에_표시한다() {
        var row = new DailyAttendance(MONDAY, true, false, 2, 0, 0, false, true);
        assertThat(build(MONDAY, MONDAY, List.of(row)).weeks().get(0).get(0))
                .isEqualTo(new CellView("event", "9/21", "◎", "9월 21일 출석, 지각 2회"));
    }

    @Test
    void 혼합발생은_조합기호와_각각의_횟수를_표시한다() {
        var row = new DailyAttendance(MONDAY, true, false, 2, 1, 1, false, true);
        assertThat(build(MONDAY, MONDAY, List.of(row)).weeks().get(0).get(0))
                .isEqualTo(new CellView("event", "9/21", "▣", "9월 21일 출석, 지각 2회, 조퇴 1회, 외출 1회"));
    }

    @Test
    void 주말훈련이_없으면_모든_주를_월요일부터_다섯칸으로_표시한다() {
        var row = new DailyAttendance(MONDAY, true, false, 0, 0, 0, false, true);
        var calendar = build(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 10, 2), List.of(row));
        assertThat(calendar.columns()).isEqualTo(5);
        assertThat(calendar.weekdays()).containsExactly("월", "화", "수", "목", "금");
        assertThat(calendar.weeks()).hasSize(3).allSatisfy(week -> assertThat(week).hasSize(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-19", "2026-09-20"})
    void 토요일이나_일요일에_미확정훈련이라도_있으면_일곱칸으로_표시한다(String dateText) {
        var row = new DailyAttendance(LocalDate.parse(dateText), false, false, 0, 0, 0, false, false);
        var calendar = build(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 10, 2), List.of(row));
        assertThat(calendar.columns()).isEqualTo(7);
        assertThat(calendar.weekdays()).containsExactly("월", "화", "수", "목", "금", "토", "일");
        assertThat(calendar.weeks()).allSatisfy(week -> assertThat(week).hasSize(7));
        assertThat(calendar.weeks().get(0).get(row.date().getDayOfWeek().getValue() - 1).kind()).isEqualTo("pending");
    }

    @Test
    void 기간밖은_빈칸이고_첫날과_월초만_월일로_표시한다() {
        var start = LocalDate.of(2026, 9, 18);
        var end = LocalDate.of(2026, 10, 6);
        var calendar = build(start, end, List.of());
        var blank = new CellView("blank", "", "", "");
        assertThat(calendar.weeks().get(0).subList(0, 4)).containsOnly(blank);
        assertThat(calendar.weeks().get(calendar.weeks().size() - 1).subList(2, 5)).containsOnly(blank);
        assertThat(calendar.weeks().get(0).get(4)).isEqualTo(new CellView("off", "9/18", "", "9월 18일 훈련 없음"));
        assertThat(cells(calendar)).contains(new CellView("off", "10/1", "", "10월 1일 훈련 없음"),
                new CellView("off", "21", "", "9월 21일 훈련 없음"));
    }

    @Test
    void 기간밖의_출결은_빈칸을_바꾸지_않는다() {
        var row = new DailyAttendance(MONDAY.minusDays(1), true, false, 1, 1, 1, false, true);
        var calendar = build(MONDAY.minusDays(3), MONDAY, List.of(row));
        assertThat(calendar.weeks().get(0).get(6).kind()).isEqualTo("event");
        var outside = build(MONDAY, MONDAY.plusDays(1), List.of(row));
        assertThat(outside.weeks().get(0).get(6)).isEqualTo(new CellView("blank", "", "", ""));
    }

    @Test
    void 종료일이_시작일보다_빠르면_거부한다() {
        assertThatThrownBy(() -> build(MONDAY, MONDAY.minusDays(1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CalendarView build(LocalDate start, LocalDate end, List<DailyAttendance> rows) {
        return AttendanceCalendar.build("기간02", "2차 · 9/21 ~ 9/25", true, start, end, rows);
    }

    private static List<CellView> cells(CalendarView calendar) {
        return calendar.weeks().stream().flatMap(List::stream).toList();
    }
}
