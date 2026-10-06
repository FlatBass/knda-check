package kr.it.acfourd.knda_check.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.schedule.TrainingDayRepository;
import kr.it.acfourd.knda_check.student.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 DB에 접속하는 테스트. 클래스 전체가 @Transactional이라 모든 변경은 끝날 때 롤백된다.
 * 항만반의 마지막 훈련일(출결이 아직 입력되지 않은 날)을 사용한다.
 */
@SpringBootTest
@Transactional
class AdminAttendanceServiceTest {

    @Autowired
    AdminAttendanceService service;
    @Autowired
    StudentRepository students;
    @Autowired
    TrainingDayRepository trainingDays;
    @Autowired
    AttendanceRepository attendances;

    @Test
    void 화면_로딩은_DB에_쓰지_않고_기본값_행을_돌려준다() {
        long before = attendances.count();

        DayEntry entry = service.loadDay(ClassCode.PORT, emptyDay());

        assertThat(entry.rows()).hasSize(rosterSize(ClassCode.PORT));
        assertThat(entry.rows()).noneMatch(r -> r.saved() || r.credit() || r.confirmed());
        assertThat(attendances.count()).isEqualTo(before);
    }

    @Test
    void 전원_출석은_모두_출석_미확정으로_채우고_다시_눌러도_변화가_없다() {
        int n = rosterSize(ClassCode.PORT);

        assertThat(service.fillAllPresent(ClassCode.PORT, emptyDay())).isEqualTo(n);

        DayEntry entry = service.loadDay(ClassCode.PORT, emptyDay());
        assertThat(entry.rows()).allMatch(r -> r.saved() && r.credit() && !r.confirmed());

        assertThat(service.fillAllPresent(ClassCode.PORT, emptyDay())).isZero();
    }

    @Test
    void 전원_출석은_먼저_입력한_공가를_덮어쓰지_않는다() {
        String first = firstStudentId(ClassCode.PORT);
        service.saveDay(ClassCode.PORT, emptyDay(),
                List.of(new RowInput(first, false, true, 0, 0, 0, false, false, "공가", null)));

        int changed = service.fillAllPresent(ClassCode.PORT, emptyDay());

        assertThat(changed).isEqualTo(rosterSize(ClassCode.PORT) - 1);
        var row = rowOf(first);
        assertThat(row.excused()).isTrue();
        assertThat(row.credit()).isFalse();
    }

    @Test
    void 전원_출석은_이미_확정한_결석을_덮어쓰지_않는다() {
        String first = firstStudentId(ClassCode.PORT);
        service.saveDay(ClassCode.PORT, emptyDay(),
                List.of(new RowInput(first, false, false, 0, 0, 0, false, true, null, null)));

        service.fillAllPresent(ClassCode.PORT, emptyDay());

        var row = rowOf(first);
        assertThat(row.confirmed()).isTrue();
        assertThat(row.credit()).isFalse();
    }

    @Test
    void 규칙에_어긋난_입력은_전체_저장을_막는다() {
        long before = attendances.count();
        String first = firstStudentId(ClassCode.PORT);

        // 출석인정과 출석인정결석이 동시에 true
        assertThatThrownBy(() -> service.saveDay(ClassCode.PORT, emptyDay(),
                List.of(new RowInput(first, true, true, 0, 0, 0, false, false, null, null))))
                .isInstanceOf(IllegalArgumentException.class);

        // 병결사용인데 출석인정결석이 아님
        assertThatThrownBy(() -> service.saveDay(ClassCode.PORT, emptyDay(),
                List.of(new RowInput(first, false, false, 0, 0, 0, true, false, null, null))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(attendances.count()).isEqualTo(before);
    }

    @Test
    void 훈련일이_아닌_날은_거부한다() {
        LocalDate saturday = LocalDate.of(2026, 8, 22);

        assertThatThrownBy(() -> service.loadDay(ClassCode.PORT, saturday))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("훈련일이 아닙니다");
    }

    @Test
    void 다른_반_수강생은_거부한다() {
        String aiStudent = firstStudentId(ClassCode.AI);

        assertThatThrownBy(() -> service.saveDay(ClassCode.PORT, emptyDay(),
                List.of(new RowInput(aiStudent, true, false, 0, 0, 0, false, false, null, null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 아무것도_입력하지_않은_행은_DB에_만들지_않는다() {
        long before = attendances.count();
        var blanks = students.findByClassCodeOrderById(ClassCode.PORT).stream()
                .map(s -> new RowInput(s.getId(), false, false, 0, 0, 0, false, false, null, null))
                .toList();

        service.saveDay(ClassCode.PORT, emptyDay(), blanks);

        assertThat(attendances.count()).isEqualTo(before);
    }

    @Test
    void 확정하며_저장하면_확정_상태로_읽힌다() {
        String first = firstStudentId(ClassCode.PORT);
        service.saveDay(ClassCode.PORT, emptyDay(),
                List.of(new RowInput(first, true, false, 1, 0, 0, false, true, null, null)));

        var row = rowOf(first);
        assertThat(row.saved()).isTrue();
        assertThat(row.confirmed()).isTrue();
        assertThat(row.late()).isEqualTo(1);
    }

    // ---- 도우미 ----

    /** 입력이 아직 없는 항만반의 마지막 훈련일 */
    private LocalDate emptyDay() {
        var list = trainingDays.findByClassCodeAndTrainingDayTrueOrderByTrainingDate(ClassCode.PORT);
        return list.get(list.size() - 1).getTrainingDate();
    }

    private int rosterSize(ClassCode classCode) {
        return students.findByClassCodeOrderById(classCode).size();
    }

    private String firstStudentId(ClassCode classCode) {
        return students.findByClassCodeOrderById(classCode).get(0).getId();
    }

    private DayEntry.Row rowOf(String studentId) {
        return service.loadDay(ClassCode.PORT, emptyDay()).rows().stream()
                .filter(r -> r.studentId().equals(studentId))
                .findFirst().orElseThrow();
    }
}