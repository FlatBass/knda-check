package kr.it.acfourd.knda_check.dashboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.it.acfourd.knda_check.attendance.DailyAttendance;
import kr.it.acfourd.knda_check.dashboard.DashboardView.CalendarView;
import kr.it.acfourd.knda_check.dashboard.DashboardView.CellView;

/** 출결 원본을 달력용 칸으로 바꾼다. 출석일수나 환산결석은 계산하지 않는다. */
final class AttendanceCalendar {
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN);
    private static final List<String> WEEKDAYS = List.of("월", "화", "수", "목", "금", "토", "일");

    private AttendanceCalendar() {
    }

    static CalendarView build(String periodId, String title, boolean current, LocalDate start, LocalDate end,
            Collection<DailyAttendance> trainingRows) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("단위기간 종료일은 시작일보다 빠를 수 없습니다.");
        }
        Map<LocalDate, DailyAttendance> rows = trainingRows.stream()
                .collect(Collectors.toMap(DailyAttendance::date, Function.identity()));
        boolean hasWeekend = trainingRows.stream()
                .anyMatch(row -> row.date().getDayOfWeek() == DayOfWeek.SATURDAY
                        || row.date().getDayOfWeek() == DayOfWeek.SUNDAY);
        int columns = hasWeekend ? 7 : 5;
        LocalDate firstMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var weeks = new ArrayList<List<CellView>>();
        for (LocalDate monday = firstMonday; !monday.isAfter(lastMonday); monday = monday.plusWeeks(1)) {
            var cells = new ArrayList<CellView>();
            for (int column = 0; column < columns; column++) {
                LocalDate date = monday.plusDays(column);
                cells.add(cell(date, start, end, rows.get(date)));
            }
            weeks.add(List.copyOf(cells));
        }
        return new CalendarView(periodId, title, current, columns, WEEKDAYS.subList(0, columns), List.copyOf(weeks));
    }

    private static CellView cell(LocalDate date, LocalDate start, LocalDate end, DailyAttendance row) {
        if (date.isBefore(start) || date.isAfter(end)) {
            return new CellView("blank", "", "", "");
        }
        String dayText = date.equals(start) || date.getDayOfMonth() == 1
                ? date.format(SHORT_DATE) : Integer.toString(date.getDayOfMonth());
        String dateLabel = date.format(LABEL_DATE);
        if (row == null) {
            return new CellView("off", dayText, "", dateLabel + " 훈련 없음");
        }
        if (!row.confirmed()) {
            return new CellView("pending", dayText, "·", dateLabel + " 아직 반영되지 않음");
        }
        if (row.excusedAbsence()) {
            return new CellView("excused", dayText, row.sickLeaveUsed() ? "병" : "공",
                    dateLabel + (row.sickLeaveUsed() ? " 인정 병결" : " 출석 인정 결석"));
        }
        if (!row.attendanceCredit()) {
            return new CellView("absent", dayText, "X", dateLabel + " 결석");
        }
        var events = new ArrayList<String>();
        if (row.lateCount() > 0) {
            events.add("지각 " + row.lateCount() + "회");
        }
        if (row.earlyLeaveCount() > 0) {
            events.add("조퇴 " + row.earlyLeaveCount() + "회");
        }
        if (row.outingCount() > 0) {
            events.add("외출 " + row.outingCount() + "회");
        }
        return new CellView(events.isEmpty() ? "present" : "event", dayText,
                eventSymbol(row.lateCount() > 0, row.earlyLeaveCount() > 0, row.outingCount() > 0),
                dateLabel + " 출석" + (events.isEmpty() ? "" : ", " + String.join(", ", events)));
    }

    /** 횟수와 무관하게 발생한 항목의 조합을 LMS 기호로 표시한다. */
    static String eventSymbol(boolean late, boolean early, boolean outing) {
        int combination = (late ? 4 : 0) | (early ? 2 : 0) | (outing ? 1 : 0);
        return switch (combination) {
            case 0 -> "○";
            case 1 -> "◇";
            case 2 -> "▲";
            case 3 -> "☆";
            case 4 -> "◎";
            case 5 -> "▶";
            case 6 -> "⊙";
            default -> "▣";
        };
    }
}
