package kr.it.acfourd.knda_check.dashboard;

import java.util.List;

/** 수강생 대시보드에 표시할 값. 날짜는 화면용 문자열로 미리 만들어 둔다. */
public record DashboardView(
        String studentId,
        String studentName,
        String className,
        boolean hasConfirmed,
        String asOfText, // "2026년 10월 2일", 확정된 출결이 없으면 null
        boolean showJudgement, // 수강 상태가 정상일 때만 80% 판정을 보여준다
        Stats overall,
        PeriodView current, // 가장 최근 확정 출결이 속한 단위기간, 없으면 null
        List<PeriodView> periods,
        SickLeave sickLeave,
        List<DayView> days) {

    public record Stats(
            int confirmedDays, int creditedDays, int extraAbsences, int finalCreditedDays,
            String ratePercent, // "87.50%", 확정일이 없으면 null
            boolean meetsThreshold, int requiredDays,
            int remainingAllowance, int eventsUntilNext,
            int convertibleEvents, // 지각·조퇴·외출 환산 대상 누적 횟수
            int totalAbsences, // 총 산정 결석일수
            int goalDays, // 기준을 채우는 데 필요한 최종 인정 출석일수
            int neededDays, // 앞으로 더 필요한 일수 (0 이상)
            int remainingDays, // 아직 확정되지 않은 남은 훈련일수
            int missableDays, // 남은 날 중 결석해도 되는 최대 일수
            boolean achieved, // 이미 기준을 채웠는가
            boolean unreachable, // 남은 날을 모두 출석해도 못 채우는가
            int creditedBarPercent, int absentBarPercent, int progressPercent) {
    }

    public record PeriodView(String id, String name, String rangeText, String status, Stats stats) {
        public boolean inProgress() {
            return "진행 중".equals(status);
        }

        public boolean completed() {
            return "확정 완료".equals(status);
        }
    }

    public record SickLeave(int used, int limit) {
        public int remaining() {
            return limit - used;
        }
    }

    public record DayView(String dateText, String label, int late, int early, int outing,
            boolean eventsExcluded, String note) {
    }
}