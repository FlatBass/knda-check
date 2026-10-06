package kr.it.acfourd.knda_check.attendance;

import java.util.OptionalDouble;

/**
 * 계산 결과, 원본 숫자 세 개만 저장하고 나머지는 모두 거기서 계산
 * AttendanceResult
 * 
 * @param confirmedDays     D : 확정된 훈련일수(소정훈련일수)
 * @param creditedDays      B : 기본 인정 출석일수 (출석인정 + 출석인정결석)
 * @param convertibleEvents E : 환산 대상 횟수
 */
public record AttendanceResult(
        int confirmedDays,
        int creditedDays,
        int convertibleEvents) {

    /** C : 지각·조퇴·외출 3회당 추가 환산결석 1일 */
    public int extraAbsences() {
        return convertibleEvents / 3;
    }

    /**
     * N: 최종 인정 출석일수 = B - C
     * 출석일(기본출석+출석인정결석)) - 환산결석(지각·조퇴·외출 3회)
     */
    public int finalCreditedDays() {
        return creditedDays - extraAbsences();
    }

    /**
     * 총 산정 결석일수 = (D - B) + C
     * (소정훈련일수 - 출석일(기본출석+출석인정결석)) + 환산결석(지각·조퇴·외출 3회)
     */
    public int totalAbsences() {
        return (confirmedDays - creditedDays) + extraAbsences();
    }

    /** 출석률(0~1). 확정된 훈련일이 없으면 비어 있다. */
    public OptionalDouble rate() {
        if (confirmedDays == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) finalCreditedDays() / confirmedDays);
    }

    /** 80% 이상 여부. 반올림 전 값으로 정수 비교한다. 확정일이 없으면 false이므로 rate()가 비었는지 먼저 확인할 것. */
    public boolean meetsThreshold() {
        return confirmedDays > 0 && finalCreditedDays() * 5 >= confirmedDays * 4;
    }

    /** 80% 유지 가능 결석일수 = FLOOR(T/5) - 현재 총 산정 결석일수. 음수도 그대로 반환. */
    public int remainingAbsenceAllowance(int totalRequiredDays) {
        return totalRequiredDays / 5 - totalAbsences();
    }

    /** 다음 추가 환산결석까지 남은 지각·조퇴·외출 횟수 */
    public int eventsUntilNextConversion() {
        return 3 - (convertibleEvents % 3);
    }

    /** 80% 기준을 채우는 데 필요한 최종 인정 출석일수 = ceil(T x 0.8) */
    public static int requiredCreditedDays(int totalRequiredDays) {
        return (totalRequiredDays * 4 + 4) / 5;
    }

    /** 앞으로 더 필요한 최종 인정 출석일수. 0 이하이면 이미 충족. */
    public int creditedDaysStillNeeded(int totalRequiredDays) {
        return requiredCreditedDays(totalRequiredDays) - finalCreditedDays();
    }

    /** 아직 확정되지 않은 남은 훈련일수 */
    public int remainingTrainingDays(int totalRequiredDays) {
        return Math.max(0, totalRequiredDays - confirmedDays);
    }

}
