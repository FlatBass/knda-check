package kr.it.acfourd.knda_check.attendance;

import java.time.LocalDate;

/** 수강생 한 명의 훈련일 하루 출결. DB·Spring에 의존하지 않는 값 객체 */
public record DailyAttendance(
        LocalDate date,
        boolean attendanceCredit, // 엑셀의 기본출석 = 출석인정
        boolean excusedAbsence, // 출석인정결석(공가, 병결 등)
        int lateCount, // 지각
        int earlyLeaveCount, // 조퇴
        int outingCount, // 외출
        boolean sickLeaveUsed, // 병결사용
        boolean confirmed // 확정여부
) {
    public DailyAttendance {
        if (attendanceCredit && excusedAbsence) {
            throw new IllegalArgumentException("출석인정과 출석인정결석은 동시에 true일 수 없습니다: " + date);
        }
        if (sickLeaveUsed && !excusedAbsence) {
            throw new IllegalArgumentException("병결사용은 출석인정결석일 때만 가능합니다: " + date);
        }
        if (lateCount < 0 || earlyLeaveCount < 0 || outingCount < 0) {
            throw new IllegalArgumentException("지각/조퇴/외출 횟수는 0 이상이어야 합니다: " + date);
        }
    }

    /** 기본 인정 출석 여부: 출석인정 또는 출석인정결석 */
    public boolean credited() {
        return attendanceCredit || excusedAbsence;
    }

    /** 환산 대상 횟수: 출석인정인 날만 카운팅. 이미 결석 처리된 날의 지각·조퇴는 제외 */
    public int convertibleEventCount() {
        return attendanceCredit ? lateCount + earlyLeaveCount + outingCount : 0;
    }
}
