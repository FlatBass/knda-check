package kr.it.acfourd.knda_check.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kr.it.acfourd.knda_check.common.BaseTimeEntity;
import kr.it.acfourd.knda_check.schedule.TrainingDay;
import kr.it.acfourd.knda_check.student.Student;

/** 수강생 한 명의 훈련일 하루 출결 (DB 테이블 attendance). */
@Entity
@Table(name = "attendance")
public class Attendance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_day_id", nullable = false)
    private TrainingDay trainingDay;

    @Column(name = "attendance_credit", nullable = false)
    private boolean attendanceCredit;

    @Column(name = "excused_absence", nullable = false)
    private boolean excusedAbsence;

    @Column(name = "late_count", nullable = false)
    private int lateCount;

    @Column(name = "early_leave_count", nullable = false)
    private int earlyLeaveCount;

    @Column(name = "outing_count", nullable = false)
    private int outingCount;

    @Column(name = "sick_leave_used", nullable = false)
    private boolean sickLeaveUsed;

    /** DB 컬럼은 is_confirmed. FALSE이면 계산·조회에서 제외된다. */
    @Column(name = "is_confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "public_note")
    private String publicNote;

    @Column(name = "internal_note")
    private String internalNote;

    protected Attendance() {
    }

    /** 기본값(출석 0, 미확정)의 새 출결 행 */
    public static Attendance create(Student student, TrainingDay trainingDay) {
        Attendance a = new Attendance();
        a.student = student;
        a.trainingDay = trainingDay;
        return a;
    }

    /** 값을 바꾼다. 규칙 위반이면 IllegalArgumentException (검증은 DailyAttendance 생성자가 담당). */
    public void apply(boolean attendanceCredit, boolean excusedAbsence, int lateCount, int earlyLeaveCount,
            int outingCount, boolean sickLeaveUsed, boolean confirmed,
            String publicNote, String internalNote) {
        new DailyAttendance(trainingDay.getTrainingDate(), attendanceCredit, excusedAbsence,
                lateCount, earlyLeaveCount, outingCount, sickLeaveUsed, confirmed);

        this.attendanceCredit = attendanceCredit;
        this.excusedAbsence = excusedAbsence;
        this.lateCount = lateCount;
        this.earlyLeaveCount = earlyLeaveCount;
        this.outingCount = outingCount;
        this.sickLeaveUsed = sickLeaveUsed;
        this.confirmed = confirmed;
        this.publicNote = blankToNull(publicNote);
        this.internalNote = blankToNull(internalNote);
    }

    /** 출석·인정결석·지각·조퇴·외출·병결이 하나도 기록되지 않은 행인가 */
    public boolean isBlank() {
        return !attendanceCredit && !excusedAbsence && lateCount == 0 && earlyLeaveCount == 0
                && outingCount == 0 && !sickLeaveUsed;
    }

    /** 전원 출석용: 기본 출석으로 표시한다. 인정결석 행에는 쓸 수 없다. */
    public void markPresent() {
        if (excusedAbsence) {
            throw new IllegalStateException("출석인정결석 행은 출석으로 바꿀 수 없습니다");
        }
        this.attendanceCredit = true;
    }

    /** 순수 계산용 객체로 변환. trainingDay를 읽으므로 트랜잭션 안에서 호출해야 한다. */
    public DailyAttendance toDaily() {
        return new DailyAttendance(
                trainingDay.getTrainingDate(),
                attendanceCredit,
                excusedAbsence,
                lateCount,
                earlyLeaveCount,
                outingCount,
                sickLeaveUsed,
                confirmed);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public TrainingDay getTrainingDay() {
        return trainingDay;
    }

    public boolean isAttendanceCredit() {
        return attendanceCredit;
    }

    public boolean isExcusedAbsence() {
        return excusedAbsence;
    }

    public int getLateCount() {
        return lateCount;
    }

    public int getEarlyLeaveCount() {
        return earlyLeaveCount;
    }

    public int getOutingCount() {
        return outingCount;
    }

    public boolean isSickLeaveUsed() {
        return sickLeaveUsed;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getPublicNote() {
        return publicNote;
    }

    public String getInternalNote() {
        return internalNote;
    }
}