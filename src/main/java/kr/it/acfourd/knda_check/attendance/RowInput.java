package kr.it.acfourd.knda_check.attendance;

/** 화면에서 저장 요청으로 들어오는 수강생 한 명의 입력값 */
public record RowInput(String studentId, boolean credit, boolean excused, int late, int early, int outing,
        boolean sick, boolean confirmed, String publicNote, String internalNote) {

    /** 아무것도 입력하지 않은 행인가 (이런 행은 DB에 새로 만들지 않는다) */
    boolean isUntouched() {
        return !credit && !excused && late == 0 && early == 0 && outing == 0 && !sick && !confirmed
                && (publicNote == null || publicNote.isBlank())
                && (internalNote == null || internalNote.isBlank());
    }
}