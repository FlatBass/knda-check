package kr.it.acfourd.knda_check.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.it.acfourd.knda_check.common.ClassCode;
import kr.it.acfourd.knda_check.student.Student;
import kr.it.acfourd.knda_check.student.StudentRepository;

@Service
public class StudentEmailService {

    private static final Logger log = LoggerFactory.getLogger(StudentEmailService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]+$");
    private static final int MAX_EMAIL_LENGTH = 254;

    public record Row(String studentId, String name, String className, String email, boolean verified) {
    }

    public record ParseResult(Map<String, String> entries, List<String> errors) {
    }

    private final StudentRepository students;
    private final EmailLoginTokenRepository tokens;
    private final Clock clock;

    public StudentEmailService(StudentRepository students, EmailLoginTokenRepository tokens, Clock clock) {
        this.students = students;
        this.tokens = tokens;
        this.clock = clock;
    }

    /** 항만반이 먼저, 그다음 AI반. 각 반 안에서는 ID 순. */
    @Transactional(readOnly = true)
    public List<Row> list() {
        return students.findAll().stream()
                .sorted(Comparator.comparing(Student::getClassCode).thenComparing(Student::getId))
                .map(s -> new Row(s.getId(), s.getName(),
                        s.getClassCode() == ClassCode.PORT ? "항만반" : "AI반",
                        s.getEmail(), s.getEmailVerifiedAt() != null))
                .toList();
    }

    /**
     * 붙여넣은 글을 읽는다. 줄마다 첫 값은 수강생 ID, 마지막 값은 이메일이고 구분은 탭·쉼표·공백이다.
     * 첫 줄이 제목 줄(@ 없음)이면 건너뛴다. DB는 건드리지 않는다.
     */
    public static ParseResult parsePaste(String text) {
        Map<String, String> entries = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        String[] lines = text == null ? new String[0] : text.split("\\R");
        boolean first = true;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            boolean isFirstLine = first;
            first = false;

            if (!line.contains("@")) {
                if (!isFirstLine) {
                    errors.add((i + 1) + "번째 줄: 이메일을 찾을 수 없습니다");
                }
                continue;
            }
            String[] tokens = line.split("[\\s,;]+");
            if (tokens.length < 2) {
                errors.add((i + 1) + "번째 줄: 수강생 ID와 이메일을 함께 적어 주세요");
                continue;
            }
            if (entries.putIfAbsent(tokens[0], tokens[tokens.length - 1]) != null) {
                errors.add((i + 1) + "번째 줄: " + tokens[0] + "이(가) 두 번 입력되었습니다");
            }
        }
        return new ParseResult(entries, errors);
    }

    /**
     * 수강생별 이메일을 저장한다. 값이 비어 있으면 이메일을 지운다. 하나라도 문제가 있으면 전부 저장하지 않는다.
     * 이메일이 바뀐 수강생은 인증 이력과 아직 쓰지 않은 로그인 링크를 초기화한다.
     *
     * @return 실제로 바뀐 수강생 수
     */
    @Transactional
    public int apply(Map<String, String> requested) {
        List<String> errors = new ArrayList<>();
        Map<String, Student> byId = students.findAll().stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        // 1. 형식 검사
        Map<String, String> wanted = new LinkedHashMap<>();      // 수강생 ID -> 새 이메일(없으면 null)
        for (Map.Entry<String, String> e : requested.entrySet()) {
            Student s = byId.get(e.getKey());
            if (s == null) {
                errors.add("등록되지 않은 수강생 ID입니다: " + e.getKey());
                continue;
            }
            String email = clean(e.getValue());
            if (email != null && (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches())) {
                errors.add(label(s) + ": 이메일 형식이 올바르지 않습니다 (" + shorten(email) + ")");
                continue;
            }
            wanted.put(s.getId(), email);
        }

        // 2. 저장 후의 전체 모습에서 같은 이메일(대소문자 무시)이 두 명에게 있는지 검사
        Map<String, String> owner = new HashMap<>();
        for (Student s : byId.values()) {
            String email = wanted.containsKey(s.getId()) ? wanted.get(s.getId()) : s.getEmail();
            if (email == null) {
                continue;
            }
            String other = owner.put(email.toLowerCase(Locale.ROOT), s.getId());
            if (other != null) {
                errors.add("같은 이메일이 두 명에게 지정되었습니다: " + label(byId.get(other)) + ", " + label(s));
            }
        }
        if (!errors.isEmpty()) {
            throw new EmailUpdateException(errors);
        }

        // 3. 실제로 바뀌는 사람만 처리
        List<Student> changed = wanted.entrySet().stream()
                .filter(e -> !Objects.equals(byId.get(e.getKey()).getEmail(), e.getValue()))
                .map(e -> byId.get(e.getKey()))
                .toList();
        if (changed.isEmpty()) {
            return 0;
        }

        // 서로 이메일을 맞바꾸는 경우에도 유일 제약에 걸리지 않도록, 먼저 모두 비우고 새 값을 넣는다
        for (Student s : changed) {
            s.changeEmail(null);
        }
        students.flush();
        for (Student s : changed) {
            String email = wanted.get(s.getId());
            if (email != null) {
                s.changeEmail(email);
            }
        }
        students.flush();

        Instant now = clock.instant();
        for (Student s : changed) {
            tokens.invalidateOpen(s.getId(), now);
        }
        log.info("수강생 이메일 {}명 변경", changed.size());     // 이메일 값은 로그에 남기지 않는다
        return changed.size();
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String s = value.strip();
        return s.isEmpty() ? null : s;
    }

    private static String label(Student s) {
        return s.getName() + "(" + s.getId() + ")";
    }

    private static String shorten(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}