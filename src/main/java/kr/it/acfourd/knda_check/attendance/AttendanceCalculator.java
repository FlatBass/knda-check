package kr.it.acfourd.knda_check.attendance;

import java.util.Collection;

public final class AttendanceCalculator {
    private AttendanceCalculator() {
    }

    /**
     * 훈련일 행만 전달해야 한다. 미확정 행은 여기서 제외한다.
     * 전체기간을 구할 때는 단위기간 결과를 합치지 말고, 전체 행을 한 번에 넘길 것.
     */
    public static AttendanceResult calculate(Collection<DailyAttendance> days) {
        int confirmed = 0;
        int credited = 0;
        int events = 0;
        for (DailyAttendance day : days) {
            if (!day.confirmed()) {
                continue;
            }
            confirmed++;
            if (day.credited()) {
                credited++;
            }
            events += day.convertibleEventCount();
        }
        return new AttendanceResult(confirmed, credited, events);
    }
}
