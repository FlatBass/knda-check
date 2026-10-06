package kr.it.acfourd.knda_check.dashboard;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import kr.it.acfourd.knda_check.attendance.Attendance;
import kr.it.acfourd.knda_check.attendance.AttendanceCalculator;
import kr.it.acfourd.knda_check.attendance.AttendanceRepository;
import kr.it.acfourd.knda_check.attendance.AttendanceResult;
import kr.it.acfourd.knda_check.attendance.DailyAttendance;
import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.dashboard.DashboardView.DayView;
import kr.it.acfourd.knda_check.dashboard.DashboardView.PeriodView;
import kr.it.acfourd.knda_check.dashboard.DashboardView.SickLeave;
import kr.it.acfourd.knda_check.dashboard.DashboardView.Stats;
import kr.it.acfourd.knda_check.schedule.UnitPeriod;
import kr.it.acfourd.knda_check.schedule.UnitPeriodRepository;
import kr.it.acfourd.knda_check.student.Student;
import kr.it.acfourd.knda_check.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentDashboardService {

        private static final String ACTIVE = "수강중";
        private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);
        private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN);
        private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN);

        private final StudentRepository students;
        private final UnitPeriodRepository unitPeriods;
        private final AttendanceRepository attendances;

        public StudentDashboardService(StudentRepository students, UnitPeriodRepository unitPeriods,
                        AttendanceRepository attendances) {
                this.students = students;
                this.unitPeriods = unitPeriods;
                this.attendances = attendances;
        }

        /** 한 수강생의 대시보드. 호출하는 쪽이 "누구의 것인지"를 인증 정보로 정해서 넘겨야 한다. */
        public DashboardView load(String studentId) {
                Student student = students.findById(studentId)
                                .orElseThrow(() -> new IllegalArgumentException("수강생을 찾을 수 없습니다: " + studentId));
                List<UnitPeriod> periods = unitPeriods.findByClassCodeOrderByPeriodNo(student.getClassCode());

                // 확정된 훈련일 출결만 사용한다. 미확정 행은 계산에도 화면에도 쓰지 않는다.
                List<Attendance> confirmed = attendances.findAllByStudentId(studentId).stream()
                                .filter(Attendance::isConfirmed)
                                .filter(a -> a.getTrainingDay().isTrainingDay())
                                .toList();

                int totalRequired = periods.stream().mapToInt(UnitPeriod::getRequiredDays).sum();

                // 전체기간은 단위기간 결과를 합치지 않고, 확정된 원본 전체로 다시 계산한다.
                Stats overall = stats(AttendanceCalculator.calculate(toDailies(confirmed)), totalRequired);

                Map<String, List<Attendance>> byPeriod = confirmed.stream()
                                .collect(Collectors.groupingBy(a -> a.getTrainingDay().getUnitPeriod().getId()));
                List<PeriodView> periodViews = periods.stream()
                                .map(p -> periodView(p, byPeriod.getOrDefault(p.getId(), List.of())))
                                .toList();

                // 현재 단위기간: 가장 최근에 확정된 출결이 속한 기간
                String latestPeriodId = confirmed.stream()
                                .max(Comparator.comparing((Attendance a) -> a.getTrainingDay().getTrainingDate()))
                                .map(a -> a.getTrainingDay().getUnitPeriod().getId())
                                .orElse(null);
                PeriodView current = periodViews.stream()
                                .filter(p -> p.id().equals(latestPeriodId))
                                .findFirst()
                                .orElse(null);

                int sickUsed = (int) confirmed.stream().filter(Attendance::isSickLeaveUsed).count();

                List<DayView> days = confirmed.stream()
                                .sorted(Comparator.comparing((Attendance a) -> a.getTrainingDay().getTrainingDate())
                                                .reversed())
                                .map(this::dayView)
                                .toList();

                String asOf = confirmed.stream()
                                .map(a -> a.getTrainingDay().getTrainingDate())
                                .max(Comparator.naturalOrder())
                                .map(d -> d.format(FULL))
                                .orElse(null);

                return new DashboardView(student.getId(), student.getName(), className(student.getClassCode()),
                                !confirmed.isEmpty(), asOf, ACTIVE.equals(student.getEnrollmentStatus()),
                                overall, current, periodViews, new SickLeave(sickUsed, student.getSickLeaveLimit()),
                                days);
        }

        private PeriodView periodView(UnitPeriod p, List<Attendance> rows) {
                AttendanceResult r = AttendanceCalculator.calculate(toDailies(rows));
                String status = r.confirmedDays() == 0 ? "확정된 출결 없음"
                                : r.confirmedDays() >= p.getRequiredDays() ? "확정 완료" : "진행 중";
                String range = p.getStartDate().format(SHORT) + " ~ " + p.getEndDate().format(SHORT);
                return new PeriodView(p.getId(), p.getPeriodNo() + "차", range, status, stats(r, p.getRequiredDays()));
        }

        private DayView dayView(Attendance a) {
                boolean hasEvents = a.getLateCount() + a.getEarlyLeaveCount() + a.getOutingCount() > 0;
                String label = a.isAttendanceCredit() ? "출석"
                                : a.isExcusedAbsence() ? (a.isSickLeaveUsed() ? "인정 병결" : "출석 인정 결석")
                                                : "결석";
                return new DayView(a.getTrainingDay().getTrainingDate().format(DAY), label,
                                a.getLateCount(), a.getEarlyLeaveCount(), a.getOutingCount(),
                                !a.isAttendanceCredit() && hasEvents, // 결석 처리일의 지각·조퇴는 환산에서 제외
                                a.getPublicNote());
        }

        private static Stats stats(AttendanceResult r, int requiredDays) {
                String rate = r.rate().isPresent()
                                ? String.format(Locale.ROOT, "%.2f%%", r.rate().getAsDouble() * 100)
                                : null;

                int goal = AttendanceResult.requiredCreditedDays(requiredDays);
                int needed = r.creditedDaysStillNeeded(requiredDays);
                int remaining = r.remainingTrainingDays(requiredDays);
                boolean achieved = needed <= 0;
                boolean unreachable = needed > remaining;
                int missable = achieved ? remaining : Math.max(remaining - needed, 0);

                int creditedBar = requiredDays == 0 ? 0 : r.finalCreditedDays() * 100 / requiredDays;
                int absentBar = requiredDays == 0 ? 0
                                : (r.confirmedDays() - r.finalCreditedDays()) * 100 / requiredDays;
                int progress = requiredDays == 0 ? 0 : (int) Math.round(100.0 * r.confirmedDays() / requiredDays);

                return new Stats(r.confirmedDays(), r.creditedDays(), r.extraAbsences(), r.finalCreditedDays(),
                                rate, r.meetsThreshold(), requiredDays,
                                r.remainingAbsenceAllowance(requiredDays), r.eventsUntilNextConversion(),
                                r.convertibleEvents(), r.totalAbsences(),
                                goal, Math.max(needed, 0), remaining, missable,
                                achieved, unreachable,
                                creditedBar, absentBar, progress);
        }

        private static List<DailyAttendance> toDailies(List<Attendance> rows) {
                return rows.stream().map(Attendance::toDaily).toList();
        }

        private static String className(ClassCode c) {
                return c == ClassCode.PORT ? "항만반" : "AI반";
        }
}