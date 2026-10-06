package kr.it.acfourd.knda_check.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttendanceCalculatorTest {

    @Test
    void 정상출석만_있으면_출석률_100퍼센트() {
        var result = AttendanceCalculator.calculate(period(0, 10, 0, 0));

        assertThat(result.confirmedDays()).isEqualTo(10);
        assertThat(result.finalCreditedDays()).isEqualTo(10);
        assertThat(result.rate().getAsDouble()).isEqualTo(1.0);
    }

    @Test
    void 지각_등_2회는_환산결석_없고_3회는_1일() {
        assertThat(AttendanceCalculator.calculate(period(0, 10, 0, 2)).extraAbsences()).isZero();
        assertThat(AttendanceCalculator.calculate(period(0, 10, 0, 3)).extraAbsences()).isEqualTo(1);
    }

    @Test
    void 결석_확정일의_지각_조퇴는_환산_횟수에서_제외() {
        var days = new ArrayList<>(period(0, 5, 0, 0));
        days.add(absentWithEvents(5, 1, 1));

        var result = AttendanceCalculator.calculate(days);

        assertThat(result.confirmedDays()).isEqualTo(6);
        assertThat(result.creditedDays()).isEqualTo(5);
        assertThat(result.convertibleEvents()).isZero();
    }

    @Test
    void 공가와_인정병결은_기본_인정_출석에_포함() {
        var days = List.of(present(0), excused(1, false), excused(2, true), absent(3));

        var result = AttendanceCalculator.calculate(days);

        assertThat(result.confirmedDays()).isEqualTo(4);
        assertThat(result.creditedDays()).isEqualTo(3);
    }

    @Test
    void 미확정_행은_분모와_분자_모두에서_제외() {
        var days = List.of(present(0), present(1), unconfirmed(2), unconfirmed(3));

        var result = AttendanceCalculator.calculate(days);

        assertThat(result.confirmedDays()).isEqualTo(2);
        assertThat(result.creditedDays()).isEqualTo(2);
    }

    @Test
    void 확정_행이_없으면_출석률은_비어_있다() {
        var result = AttendanceCalculator.calculate(List.of(unconfirmed(0)));

        assertThat(result.rate()).isEmpty();
    }

    @Test
    void 전체기간은_단위기간_결과를_합치지_않고_원본에서_다시_환산() {
        var first = period(0, 10, 0, 2);
        var second = period(10, 10, 0, 2);

        assertThat(AttendanceCalculator.calculate(first).extraAbsences()).isZero();
        assertThat(AttendanceCalculator.calculate(second).extraAbsences()).isZero();

        var all = new ArrayList<>(first);
        all.addAll(second);
        assertThat(AttendanceCalculator.calculate(all).extraAbsences()).isEqualTo(1);
    }

    @Test
    void 문서_9장_검증_예시() {
        var first = period(0, 23, 1, 4);
        var second = period(23, 9, 1, 2);
        var all = new ArrayList<>(first);
        all.addAll(second);

        var r1 = AttendanceCalculator.calculate(first);
        assertThat(r1).isEqualTo(new AttendanceResult(23, 22, 4));
        assertThat(r1.extraAbsences()).isEqualTo(1);
        assertThat(r1.finalCreditedDays()).isEqualTo(21);
        assertThat(r1.rate().getAsDouble()).isCloseTo(0.9130, within(0.0001));

        var r2 = AttendanceCalculator.calculate(second);
        assertThat(r2).isEqualTo(new AttendanceResult(9, 8, 2));
        assertThat(r2.extraAbsences()).isZero();
        assertThat(r2.finalCreditedDays()).isEqualTo(8);
        assertThat(r2.rate().getAsDouble()).isCloseTo(0.8889, within(0.0001));

        var total = AttendanceCalculator.calculate(all);
        assertThat(total).isEqualTo(new AttendanceResult(32, 30, 6));
        assertThat(total.extraAbsences()).isEqualTo(2);
        assertThat(total.finalCreditedDays()).isEqualTo(28);
        assertThat(total.rate().getAsDouble()).isEqualTo(0.875);
    }

    @Test
    void 팔십퍼센트_판정_경계값() {
        assertThat(new AttendanceResult(17, 14, 0).meetsThreshold()).isTrue();
        assertThat(new AttendanceResult(17, 13, 0).meetsThreshold()).isFalse();
        assertThat(new AttendanceResult(10, 8, 0).meetsThreshold()).isTrue();
    }

    @Test
    void 유지_가능_결석일수와_다음_환산까지_횟수() {
        var result = new AttendanceResult(9, 8, 2);

        assertThat(result.remainingAbsenceAllowance(17)).isEqualTo(2);
        assertThat(result.eventsUntilNextConversion()).isEqualTo(1);
    }

    @Test
    void 잘못된_출결_값은_생성_자체를_막는다() {
        assertThatThrownBy(() -> new DailyAttendance(day(0), true, true, 0, 0, 0, false, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyAttendance(day(0), false, false, 0, 0, 0, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyAttendance(day(0), true, false, -1, 0, 0, false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 테스트용 도우미 ----

    private static final LocalDate START = LocalDate.of(2026, 8, 18);

    private static LocalDate day(int i) {
        return START.plusDays(i);
    }

    private static DailyAttendance present(int i) {
        return new DailyAttendance(day(i), true, false, 0, 0, 0, false, true);
    }

    private static DailyAttendance presentWithLate(int i, int late) {
        return new DailyAttendance(day(i), true, false, late, 0, 0, false, true);
    }

    private static DailyAttendance absent(int i) {
        return new DailyAttendance(day(i), false, false, 0, 0, 0, false, true);
    }

    private static DailyAttendance absentWithEvents(int i, int late, int early) {
        return new DailyAttendance(day(i), false, false, late, early, 0, false, true);
    }

    private static DailyAttendance excused(int i, boolean sick) {
        return new DailyAttendance(day(i), false, true, 0, 0, 0, sick, true);
    }

    private static DailyAttendance unconfirmed(int i) {
        return new DailyAttendance(day(i), false, false, 0, 0, 0, false, false);
    }

    /** totalDays일짜리 기간: 앞에서부터 결석 absentDays일, 그다음 지각 1회짜리 lateDays일, 나머지는 정상 출석 */
    private static List<DailyAttendance> period(int startIndex, int totalDays, int absentDays, int lateDays) {
        var list = new ArrayList<DailyAttendance>();
        for (int i = 0; i < totalDays; i++) {
            int idx = startIndex + i;
            if (i < absentDays) {
                list.add(absent(idx));
            } else if (i < absentDays + lateDays) {
                list.add(presentWithLate(idx, 1));
            } else {
                list.add(present(idx));
            }
        }
        return list;
    }
}
