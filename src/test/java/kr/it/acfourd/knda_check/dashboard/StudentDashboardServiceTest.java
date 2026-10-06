package kr.it.acfourd.knda_check.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.schedule.TrainingDay;
import kr.it.acfourd.knda_check.schedule.TrainingDayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 DB에 접속하는 테스트. 테스트용 수강생(T001)과 출결은 끝날 때 롤백된다. */
@SpringBootTest
@Transactional
class StudentDashboardServiceTest {

    @Autowired
    StudentDashboardService service;
    @Autowired
    TrainingDayRepository days;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 확정된_출결만_계산하고_표시한다() {
        insertStudent("T001");
        var d = days.findByClassCodeAndTrainingDayTrueOrderByTrainingDate(ClassCode.PORT);

        // credit excused late early out sick confirmed
        insert("T001", d.get(0), true, false, 0, 0, 0, false, true); // 정상 출석
        insert("T001", d.get(1), true, false, 1, 0, 0, false, true); // 지각 1회 (환산 대상)
        insert("T001", d.get(2), false, false, 1, 0, 0, false, true); // 결석 (지각은 환산 제외)
        insert("T001", d.get(3), false, true, 0, 0, 0, true, true); // 인정 병결
        insert("T001", d.get(4), true, false, 0, 0, 0, false, false); // 미확정: 보이지도 계산되지도 않는다

        var view = service.load("T001");

        assertThat(view.hasConfirmed()).isTrue();
        assertThat(view.overall().confirmedDays()).isEqualTo(4);
        assertThat(view.overall().creditedDays()).isEqualTo(3);
        assertThat(view.overall().extraAbsences()).isZero();
        assertThat(view.overall().ratePercent()).isEqualTo("75.00%");
        assertThat(view.overall().meetsThreshold()).isFalse();
        assertThat(view.overall().requiredDays()).isEqualTo(63); // 23 + 17 + 22 + 1
        assertThat(view.overall().remainingAllowance()).isEqualTo(11); // FLOOR(63/5) = 12, 현재 총 산정 결석 1
        assertThat(view.overall().eventsUntilNext()).isEqualTo(2); // 환산 대상은 1회뿐

        assertThat(view.sickLeave().used()).isEqualTo(1);
        assertThat(view.sickLeave().remaining()).isEqualTo(5);

        assertThat(view.days()).hasSize(4);
        assertThat(view.days().get(0).label()).isEqualTo("인정 병결"); // 최신 날짜가 먼저
        assertThat(view.days().get(1).eventsExcluded()).isTrue(); // 결석일의 지각은 환산 제외 표시

        assertThat(view.periods().get(0).status()).isEqualTo("진행 중");
        assertThat(view.periods().get(0).stats().confirmedDays()).isEqualTo(4);
        assertThat(view.periods().get(1).status()).isEqualTo("확정된 출결 없음");
    }

    @Test
    void 확정된_출결이_없으면_점수를_계산하지_않는다() {
        insertStudent("T001");

        var view = service.load("T001");

        assertThat(view.hasConfirmed()).isFalse();
        assertThat(view.overall().ratePercent()).isNull();
        assertThat(view.asOfText()).isNull();
        assertThat(view.days()).isEmpty();
        assertThat(view.sickLeave().used()).isZero();
        assertThat(view.current()).isNull();
    }

    @Test
    void 전체기간은_단위기간_결과를_합치지_않고_다시_환산한다() {
        insertStudent("T001");
        var all = days.findByClassCodeAndTrainingDayTrueOrderByTrainingDate(ClassCode.PORT);
        var p1 = all.stream().filter(x -> x.getUnitPeriod().getId().equals("항만01")).toList();
        var p2 = all.stream().filter(x -> x.getUnitPeriod().getId().equals("항만02")).toList();

        // 1차에 지각 2회, 2차에 지각 2회 -> 각 기간 환산 0일, 전체는 4회라서 1일
        insert("T001", p1.get(p1.size() - 2), true, false, 1, 0, 0, false, true);
        insert("T001", p1.get(p1.size() - 1), true, false, 1, 0, 0, false, true);
        insert("T001", p2.get(0), true, false, 1, 0, 0, false, true);
        insert("T001", p2.get(1), true, false, 1, 0, 0, false, true);

        var view = service.load("T001");

        assertThat(view.periods().get(0).stats().extraAbsences()).isZero();
        assertThat(view.periods().get(1).stats().extraAbsences()).isZero();
        assertThat(view.overall().extraAbsences()).isEqualTo(1);
        assertThat(view.overall().finalCreditedDays()).isEqualTo(3);
        assertThat(view.overall().ratePercent()).isEqualTo("75.00%");
    }

    @Test
    void 현재_단위기간은_가장_최근_확정일이_속한_기간이고_그_기간만으로_계산한다() {
        insertStudent("T001");
        var all = days.findByClassCodeAndTrainingDayTrueOrderByTrainingDate(ClassCode.PORT);
        var p1 = all.stream().filter(x -> x.getUnitPeriod().getId().equals("항만01")).toList();
        var p2 = all.stream().filter(x -> x.getUnitPeriod().getId().equals("항만02")).toList();

        // credit excused late early out sick confirmed
        insert("T001", p1.get(p1.size() - 1), true, false, 1, 0, 0, false, true); // 1차 마지막 날: 지각 1
        insert("T001", p2.get(0), true, false, 0, 0, 0, false, true); // 2차: 출석
        insert("T001", p2.get(1), true, false, 1, 0, 0, false, true); // 2차: 지각 1
        insert("T001", p2.get(2), false, false, 0, 0, 0, false, true); // 2차: 결석

        var view = service.load("T001");

        // 가장 최근 확정일(2차 3번째 날)이 속한 2차가 현재 단위기간
        assertThat(view.current().name()).isEqualTo("2차");

        // 2차만으로 계산: 확정 3, 인정 2, 지각 1회 (1차의 지각은 포함하지 않음)
        var c = view.current().stats();
        assertThat(c.confirmedDays()).isEqualTo(3);
        assertThat(c.finalCreditedDays()).isEqualTo(2);
        assertThat(c.convertibleEvents()).isEqualTo(1);
        assertThat(c.ratePercent()).isEqualTo("66.67%");
        assertThat(c.requiredDays()).isEqualTo(17);
        assertThat(c.goalDays()).isEqualTo(14);
        assertThat(c.neededDays()).isEqualTo(12);
        assertThat(c.remainingDays()).isEqualTo(14);
        assertThat(c.missableDays()).isEqualTo(2);

        // 전체는 지각 2회를 합쳐서 계산 (환산 0일)
        assertThat(view.overall().convertibleEvents()).isEqualTo(2);
        assertThat(view.overall().extraAbsences()).isZero();
    }

    // ---- 도우미 ----

    private void insertStudent(String id) {
        jdbc.update("""
                insert into student (id, class_code, name, sick_leave_limit)
                values (?, 'PORT', '테스트', 6)
                """, id);
    }

    private void insert(String studentId, TrainingDay day, boolean credit, boolean excused,
            int late, int early, int outing, boolean sick, boolean confirmed) {
        jdbc.update("""
                insert into attendance (student_id, training_day_id, attendance_credit, excused_absence,
                                        late_count, early_leave_count, outing_count, sick_leave_used, is_confirmed)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, studentId, day.getId(), credit, excused, late, early, outing, sick, confirmed);
    }
}