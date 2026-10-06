package kr.it.acfourd.knda_check.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class StudentEmailPasteTest {

    @Test
    void 탭_쉼표_공백으로_구분된_줄을_읽고_마지막_값을_이메일로_본다() {
        var r = StudentEmailService.parsePaste("P001\ta@x.com\nP002, b@x.com\nP003 홍 길동 c@x.com\n");

        assertThat(r.errors()).isEmpty();
        assertThat(r.entries()).containsExactly(
                Map.entry("P001", "a@x.com"), Map.entry("P002", "b@x.com"), Map.entry("P003", "c@x.com"));
    }

    @Test
    void 첫_줄이_제목이면_건너뛰고_빈_줄은_무시한다() {
        var r = StudentEmailService.parsePaste("\nID\t이름\t이메일\n\nP001\t홍길동\ta@x.com\n");

        assertThat(r.errors()).isEmpty();
        assertThat(r.entries()).containsOnlyKeys("P001");
    }

    @Test
    void 문제가_있는_줄은_줄_번호와_함께_알려준다() {
        var r = StudentEmailService.parsePaste("P001 a@x.com\nP001 b@x.com\nonlyone@x.com\nP002 no-email");

        assertThat(r.errors()).hasSize(3);
        assertThat(r.errors().get(0)).startsWith("2번째 줄");
        assertThat(r.errors().get(1)).startsWith("3번째 줄");
        assertThat(r.errors().get(2)).startsWith("4번째 줄");
    }
}