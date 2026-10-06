package kr.it.acfourd.knda_check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.it.acfourd.knda_check.attendance.Attendance;
import kr.it.acfourd.knda_check.attendance.AttendanceCalculator;
import kr.it.acfourd.knda_check.attendance.AttendanceRepository;
import kr.it.acfourd.knda_check.attendance.AttendanceResult;
import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.schedule.TrainingDay;
import kr.it.acfourd.knda_check.schedule.TrainingDayRepository;
import kr.it.acfourd.knda_check.schedule.UnitPeriod;
import kr.it.acfourd.knda_check.schedule.UnitPeriodRepository;
import kr.it.acfourd.knda_check.student.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 DB(Cloudtype)에 접속하는 통합 테스트. 시드 데이터가 들어 있어야 통과한다.
 * 클래스 전체가 @Transactional이라 테스트가 쓴 행은 끝날 때 모두 롤백된다.
 */
@SpringBootTest
@Transactional
class DatabaseIntegrationTest {

    @Autowired
    StudentRepository students;
    @Autowired
    UnitPeriodRepository periods;
    @Autowired
    TrainingDayRepository days;
    @Autowired
    AttendanceRepository attendances;
    @Autowired
    JdbcTemplate jdbc;

    // ---- 시드 데이터 읽기 ----

    @Test
    void 시드_수강생은_39명이고_병결한도는_반별로_6일_8일() {
        assertThat(students.count()).isEqualTo(39);

        var port = students.findByClassCodeOrderById(ClassCode.PORT);
        var ai = students.findByClassCodeOrderById(ClassCode.AI);
        assertThat(port).hasSize(24);
        assertThat(ai).hasSize(15);
        assertThat(port).allMatch(s -> s.getSickLeaveLimit() == 6);
        assertThat(ai).allMatch(s -> s.getSickLeaveLimit() == 8);
    }

    @Test
    void 시드_훈련일정은_202행이고_단위기간의_소정훈련일수와_일치() {
        assertThat(days.count()).isEqualTo(202);
        assertThat(periods.count()).isEqualTo(8);

        for (UnitPeriod p : periods.findAll()) {
            assertThat(days.countByUnitPeriodIdAndTrainingDayTrue(p.getId()))
                    .as(p.getId())
                    .isEqualTo(p.getRequiredDays());
        }
    }

    // ---- 엔티티 -> 계산기 연결 ----

    @Test
    void 엔티티에서_읽은_출결이_계산기까지_연결된다() {
        insertTestStudent("T001", null);
        var d = days.findByClassCodeAndTrainingDayTrueOrderByTrainingDate(ClassCode.PORT);

        // credit excused late early out sick confirmed
        insertAttendance("T001", d.get(0), true, false, 0, 0, 0, false, true); // 정상 출석
        insertAttendance("T001", d.get(1), true, false, 1, 0, 0, false, true); // 지각 1회 (환산 대상)
        insertAttendance("T001", d.get(2), false, false, 1, 0, 0, false, true); // 결석 확정 (지각 이력은 환산 제외)
        insertAttendance("T001", d.get(3), false, true, 0, 0, 0, false, true); // 공가
        insertAttendance("T001", d.get(4), false, false, 0, 0, 0, false, false); // 미확정

        var daily = attendances.findAllByStudentId("T001").stream().map(Attendance::toDaily).toList();
        var result = AttendanceCalculator.calculate(daily);

        assertThat(daily).hasSize(5);
        assertThat(result).isEqualTo(new AttendanceResult(4, 3, 1));
        assertThat(result.rate().getAsDouble()).isEqualTo(0.75);
    }

    // ---- DB 제약 (실패하는 INSERT 하나만 담는다: PostgreSQL은 오류 후 트랜잭션이 중단됨) ----

    @Test
    void 출석인정과_출석인정결석이_동시에_true이면_DB가_거부() {
        insertTestStudent("T001", null);
        var day = firstTrainingDay();

        assertThatThrownBy(() -> insertAttendance("T001", day, true, true, 0, 0, 0, false, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 병결사용인데_출석인정결석이_아니면_DB가_거부() {
        insertTestStudent("T001", null);
        var day = firstTrainingDay();

        assertThatThrownBy(() -> insertAttendance("T001", day, false, false, 0, 0, 0, true, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_수강생의_같은_날짜_출결_중복을_DB가_거부() {
        insertTestStudent("T001", null);
        var day = firstTrainingDay();
        insertAttendance("T001", day, true, false, 0, 0, 0, false, true);

        assertThatThrownBy(() -> insertAttendance("T001", day, true, false, 0, 0, 0, false, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 이메일은_대소문자와_관계없이_중복_등록_불가() {
        insertTestStudent("T001", "test@example.com");

        assertThatThrownBy(() -> insertTestStudent("T002", "TEST@Example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- 도우미 ----

    private TrainingDay firstTrainingDay() {
        return days.findByClassCodeAndTrainingDayTrueOrderByTrainingDate(ClassCode.PORT).get(0);
    }

    private void insertTestStudent(String id, String email) {
        jdbc.update("""
                insert into student (id, class_code, name, sick_leave_limit, email)
                values (?, 'PORT', '테스트', 6, ?)
                """, id, email);
    }

    private void insertAttendance(String studentId, TrainingDay day, boolean credit, boolean excused,
            int late, int early, int outing, boolean sick, boolean confirmed) {
        jdbc.update("""
                insert into attendance (student_id, training_day_id, attendance_credit, excused_absence,
                                        late_count, early_leave_count, outing_count, sick_leave_used, is_confirmed)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, studentId, day.getId(), credit, excused, late, early, outing, sick, confirmed);
    }
}