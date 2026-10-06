package kr.it.acfourd.knda_check.attendance;

import java.time.LocalDate;
import java.util.List;
import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.student.Student;

/** 일일 입력 화면에 보여줄 데이터. 엔티티를 화면으로 내보내지 않으려고 값만 담는다. */
public record DayEntry(ClassCode classCode, LocalDate date, List<Row> rows) {

    /** saved = false이면 아직 DB에 행이 없는 기본값(출석 0, 미확정)이다. */
    public record Row(String studentId, String studentName, boolean saved,
            boolean credit, boolean excused, int late, int early, int outing,
            boolean sick, boolean confirmed, String publicNote, String internalNote) {

        static Row blank(Student s) {
            return new Row(s.getId(), s.getName(), false, false, false, 0, 0, 0, false, false, null, null);
        }

        static Row from(Attendance a) {
            return new Row(a.getStudent().getId(), a.getStudent().getName(), true,
                    a.isAttendanceCredit(), a.isExcusedAbsence(), a.getLateCount(), a.getEarlyLeaveCount(),
                    a.getOutingCount(), a.isSickLeaveUsed(), a.isConfirmed(), a.getPublicNote(), a.getInternalNote());
        }
    }
}