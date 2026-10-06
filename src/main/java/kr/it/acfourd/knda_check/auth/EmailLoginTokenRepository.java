package kr.it.acfourd.knda_check.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailLoginTokenRepository extends JpaRepository<EmailLoginToken, Long> {

    Optional<EmailLoginToken> findByTokenHash(String tokenHash);

    long countByStudentIdAndCreatedAtAfter(String studentId, Instant after);

    /** 아직 쓰지 않은 이전 링크를 모두 무효로 만든다. */
    @Modifying(flushAutomatically = true)
    @Query("update EmailLoginToken t set t.usedAt = :now where t.studentId = :studentId and t.usedAt is null")
    int invalidateOpen(@Param("studentId") String studentId, @Param("now") Instant now);

    /** 한 번만, 만료 전에만 성공한다. 1이면 성공, 0이면 이미 사용했거나 만료. 동시에 눌러도 한 요청만 성공한다. */
    @Modifying(flushAutomatically = true)
    @Query("update EmailLoginToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int consume(@Param("id") Long id, @Param("now") Instant now);
}