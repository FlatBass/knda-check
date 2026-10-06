-- 수강생
CREATE TABLE student (
    id                  VARCHAR(20)  PRIMARY KEY,                -- P001, A001 (문자열 ID)
    class_code          VARCHAR(10)  NOT NULL,
    name                VARCHAR(50)  NOT NULL,
    sick_leave_limit    INTEGER      NOT NULL,
    enrollment_status   VARCHAR(20)  NOT NULL DEFAULT '수강중',
    email               VARCHAR(254),                            -- 미등록이면 NULL
    email_verified_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_student_class CHECK (class_code IN ('PORT', 'AI')),
    CONSTRAINT ck_student_sick_limit CHECK (sick_leave_limit >= 0)
);

-- 이메일 중복 연결 금지 (대소문자 무시, NULL은 여러 개 허용)
CREATE UNIQUE INDEX uq_student_email ON student (lower(email));

-- 단위기간
CREATE TABLE unit_period (
    id             VARCHAR(20) PRIMARY KEY,                      -- 항만01, AI01
    class_code     VARCHAR(10) NOT NULL,
    period_no      INTEGER     NOT NULL,
    start_date     DATE        NOT NULL,
    end_date       DATE        NOT NULL,
    required_days  INTEGER     NOT NULL,                         -- 확정 소정훈련일수
    CONSTRAINT ck_period_class CHECK (class_code IN ('PORT', 'AI')),
    CONSTRAINT ck_period_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_period_days CHECK (required_days >= 0),
    CONSTRAINT uq_period_class_no UNIQUE (class_code, period_no),
    CONSTRAINT uq_period_id_class UNIQUE (id, class_code)        -- 아래 복합 FK용
);

-- 훈련일정 (비훈련일 포함, 모든 날짜가 소속 단위기간을 가짐)
CREATE TABLE training_day (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_code       VARCHAR(10) NOT NULL,
    training_date    DATE        NOT NULL,
    unit_period_id   VARCHAR(20) NOT NULL,
    is_training_day  BOOLEAN     NOT NULL,
    CONSTRAINT uq_training_day UNIQUE (class_code, training_date),
    -- 훈련일정의 반과 단위기간의 반이 일치하도록 DB가 보장
    CONSTRAINT fk_training_day_period FOREIGN KEY (unit_period_id, class_code)
        REFERENCES unit_period (id, class_code)
);

CREATE INDEX ix_training_day_period ON training_day (unit_period_id);

-- 일별 출결 (수강생 1명 x 훈련일 1일)
CREATE TABLE attendance (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id         VARCHAR(20) NOT NULL REFERENCES student (id),
    training_day_id    BIGINT      NOT NULL REFERENCES training_day (id),

    attendance_credit  BOOLEAN     NOT NULL DEFAULT FALSE,       -- 엑셀의 기본출석(출석인정)
    excused_absence    BOOLEAN     NOT NULL DEFAULT FALSE,       -- 출석인정결석
    late_count         INTEGER     NOT NULL DEFAULT 0,
    early_leave_count  INTEGER     NOT NULL DEFAULT 0,
    outing_count       INTEGER     NOT NULL DEFAULT 0,
    sick_leave_used    BOOLEAN     NOT NULL DEFAULT FALSE,
    is_confirmed       BOOLEAN     NOT NULL DEFAULT FALSE,       -- FALSE면 계산·조회 제외

    public_note        TEXT,                                     -- 수강생에게 표시할 사유
    internal_note      TEXT,                                     -- 관리자용 메모

    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_attendance UNIQUE (student_id, training_day_id),
    CONSTRAINT ck_att_credit_excused CHECK (NOT (attendance_credit AND excused_absence)),
    CONSTRAINT ck_att_sick_needs_excused CHECK (NOT sick_leave_used OR excused_absence),
    CONSTRAINT ck_att_counts CHECK (late_count >= 0 AND early_leave_count >= 0 AND outing_count >= 0)
);

CREATE INDEX ix_attendance_training_day ON attendance (training_day_id);