-- 이메일 로그인 링크 토큰. 원문 토큰은 저장하지 않고 SHA-256 해시만 저장한다.
CREATE TABLE email_login_token (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id    VARCHAR(20)  NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    target_email  VARCHAR(254) NOT NULL,      -- 발송 당시의 이메일 (이후 바뀌면 링크 무효)
    token_hash    VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_login_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_login_token_student ON email_login_token (student_id, created_at);