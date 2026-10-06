package kr.it.acfourd.knda_check.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.it.acfourd.knda_check.student.Student;
import kr.it.acfourd.knda_check.student.StudentRepository;

@Service
public class StudentLoginService {

    private static final Logger log = LoggerFactory.getLogger(StudentLoginService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    static final Duration VALIDITY = Duration.ofMinutes(10);
    static final int MAX_REQUESTS_PER_WINDOW = 3;     // 한 수강생이 10분 안에 요청할 수 있는 횟수

    private final StudentRepository students;
    private final EmailLoginTokenRepository tokens;
    private final LoginMailSender mailSender;
    private final Clock clock;
    private final String baseUrl;

    public StudentLoginService(StudentRepository students, EmailLoginTokenRepository tokens,
                               LoginMailSender mailSender, Clock clock,
                               @Value("${app.base-url}") String baseUrl) {
        this.students = students;
        this.tokens = tokens;
        this.mailSender = mailSender;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 로그인 링크를 요청한다. 등록되지 않은 이메일이거나 요청 횟수를 넘었어도 예외 없이 조용히 끝난다
     * (호출하는 쪽이 항상 같은 안내문을 보여주도록).
     */
    @Transactional
    public void requestLink(String rawEmail) {
        String email = normalize(rawEmail);
        if (email.isEmpty() || email.length() > 254) {
            return;
        }
        Optional<Student> found = students.findByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            return;
        }
        Student student = found.get();
        Instant now = clock.instant();

        if (tokens.countByStudentIdAndCreatedAtAfter(student.getId(), now.minus(VALIDITY)) >= MAX_REQUESTS_PER_WINDOW) {
            log.warn("로그인 링크 요청 횟수 초과: {}", student.getId());
            return;
        }

        tokens.invalidateOpen(student.getId(), now);          // 가장 최근 링크만 유효
        String rawToken = newToken();
        tokens.save(new EmailLoginToken(student.getId(), student.getEmail(), sha256(rawToken), now, now.plus(VALIDITY)));

        String link = baseUrl + "/auth/verify?token=" + rawToken;
        try {
            mailSender.sendLoginLink(student.getEmail(), student.getName(), link, VALIDITY);
        } catch (RuntimeException e) {
            log.error("로그인 링크 메일 발송 실패: {}", student.getId(), e);
        }
    }

    /** 링크의 토큰을 확인하고 사용 처리한다. 성공하면 수강생 ID, 실패하면 빈 값. 한 번 성공하면 다시 쓸 수 없다. */
    @Transactional
    public Optional<String> consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 100) {
            return Optional.empty();
        }
        Optional<EmailLoginToken> found = tokens.findByTokenHash(sha256(rawToken.strip()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        EmailLoginToken token = found.get();

        // 링크를 보낸 뒤 관리자가 이메일을 바꾸거나 지웠다면 그 링크는 무효
        Optional<Student> student = students.findById(token.getStudentId());
        if (student.isEmpty() || student.get().getEmail() == null
                || !student.get().getEmail().equalsIgnoreCase(token.getTargetEmail())) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        if (tokens.consume(token.getId(), now) != 1) {
            return Optional.empty();                           // 이미 사용했거나 만료
        }
        if (student.get().getEmailVerifiedAt() == null) {
            student.get().markEmailVerified(now);
        }
        return Optional.of(student.get().getId());
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}