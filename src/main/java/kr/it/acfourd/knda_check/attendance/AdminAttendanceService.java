package kr.it.acfourd.knda_check.attendance;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.schedule.TrainingDay;
import kr.it.acfourd.knda_check.schedule.TrainingDayRepository;
import kr.it.acfourd.knda_check.student.Student;
import kr.it.acfourd.knda_check.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAttendanceService {

    private final StudentRepository students;
    private final TrainingDayRepository trainingDays;
    private final AttendanceRepository attendances;

    public AdminAttendanceService(StudentRepository students,
            TrainingDayRepository trainingDays,
            AttendanceRepository attendances) {
        this.students = students;
        this.trainingDays = trainingDays;
        this.attendances = attendances;
    }

    /** 반·날짜의 입력 화면 데이터. DB에 아무것도 쓰지 않는다. */
    public DayEntry loadDay(ClassCode classCode, LocalDate date) {
        TrainingDay day = requireTrainingDay(classCode, date);
        Map<String, Attendance> existing = existingByStudent(day);

        List<DayEntry.Row> rows = students.findByClassCodeOrderById(classCode).stream()
                .map(s -> existing.containsKey(s.getId())
                        ? DayEntry.Row.from(existing.get(s.getId()))
                        : DayEntry.Row.blank(s))
                .toList();
        return new DayEntry(classCode, date, rows);
    }

    /**
     * 반 전원 출석. 행이 없거나, 미확정이면서 예외 기록이 하나도 없는 행만 출석으로 채운다.
     * 확정된 행, 이미 값을 입력한 행(공가·지각 등)은 건드리지 않는다. 확정은 하지 않는다.
     *
     * @return 변경된(또는 새로 만든) 행 수
     */
    @Transactional
    public int fillAllPresent(ClassCode classCode, LocalDate date) {
        TrainingDay day = requireTrainingDay(classCode, date);
        Map<String, Attendance> existing = existingByStudent(day);

        int changed = 0;
        for (Student s : students.findByClassCodeOrderById(classCode)) {
            Attendance a = existing.get(s.getId());
            if (a == null) {
                a = Attendance.create(s, day);
                a.markPresent();
                attendances.save(a);
                changed++;
            } else if (!a.isConfirmed() && a.isBlank()) {
                a.markPresent();
                changed++;
            }
        }
        return changed;
    }

    /**
     * 그날의 입력을 저장한다. 하나라도 규칙에 어긋나면 전부 저장하지 않는다.
     * 아무것도 입력하지 않은 새 행은 만들지 않는다.
     */
    @Transactional
    public void saveDay(ClassCode classCode, LocalDate date, List<RowInput> inputs) {
        TrainingDay day = requireTrainingDay(classCode, date);
        Map<String, Student> roster = students.findByClassCodeOrderById(classCode).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<String, Attendance> existing = existingByStudent(day);

        Set<String> seen = new HashSet<>();
        for (RowInput in : inputs) {
            Student s = roster.get(in.studentId());
            if (s == null) {
                throw new IllegalArgumentException("해당 반의 수강생이 아닙니다: " + in.studentId());
            }
            if (!seen.add(in.studentId())) {
                throw new IllegalArgumentException("같은 수강생이 중복 입력되었습니다: " + in.studentId());
            }

            Attendance a = existing.get(in.studentId());
            if (a == null) {
                if (in.isUntouched()) {
                    continue;
                }
                a = Attendance.create(s, day);
                a.apply(in.credit(), in.excused(), in.late(), in.early(), in.outing(), in.sick(),
                        in.confirmed(), in.publicNote(), in.internalNote());
                attendances.save(a);
            } else {
                a.apply(in.credit(), in.excused(), in.late(), in.early(), in.outing(), in.sick(),
                        in.confirmed(), in.publicNote(), in.internalNote());
            }
        }
    }

    private TrainingDay requireTrainingDay(ClassCode classCode, LocalDate date) {
        return trainingDays.findByClassCodeAndTrainingDate(classCode, date)
                .filter(TrainingDay::isTrainingDay)
                .orElseThrow(() -> new IllegalArgumentException("훈련일이 아닙니다: " + classCode + " " + date));
    }

    private Map<String, Attendance> existingByStudent(TrainingDay day) {
        return attendances.findAllByTrainingDayId(day.getId()).stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity()));
    }
}