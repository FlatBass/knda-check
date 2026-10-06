package kr.it.acfourd.knda_check.auth;

import java.util.List;

/** 이메일 저장 요청에 문제가 있을 때. 문제 목록을 모두 담아서 화면에 한꺼번에 보여준다. */
public class EmailUpdateException extends RuntimeException {

    private final List<String> errors;

    public EmailUpdateException(List<String> errors) {
        super(String.join(" / ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}