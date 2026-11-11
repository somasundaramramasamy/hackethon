-- Kavasam core schema: test runs, review cases and human reviews.
-- utf8mb4 is required so Tamil and Hindi text is stored correctly.

-- ─────────────── Test runs (Dashboard, Test Runs page) ───────────────

-- One batch of testing on a topic across languages.
CREATE TABLE test_run (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(32)  NOT NULL,
    suite         VARCHAR(120) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    started_at    DATETIME(6)  NOT NULL,
    safety_score  DOUBLE       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_test_run_code UNIQUE (code),
    INDEX idx_test_run_started_at (started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Results of a run in each language (EN, TA, HI). needs review = total - passed - failed.
CREATE TABLE test_run_language_result (
    test_run_id   BIGINT      NOT NULL,
    position      INT         NOT NULL,
    language      VARCHAR(8)  NOT NULL,
    total_tests   INT         NOT NULL,
    passed_tests  INT         NOT NULL,
    failed_tests  INT         NOT NULL,
    PRIMARY KEY (test_run_id, position),
    CONSTRAINT fk_language_result_run FOREIGN KEY (test_run_id) REFERENCES test_run (id) ON DELETE CASCADE,
    CONSTRAINT chk_language_result_counts CHECK (passed_tests >= 0 AND failed_tests >= 0
        AND passed_tests + failed_tests <= total_tests)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- How many failures of each type a run had.
CREATE TABLE test_run_failure (
    test_run_id    BIGINT      NOT NULL,
    position       INT         NOT NULL,
    category       VARCHAR(40) NOT NULL,
    failure_count  INT         NOT NULL,
    PRIMARY KEY (test_run_id, position),
    CONSTRAINT fk_failure_run FOREIGN KEY (test_run_id) REFERENCES test_run (id) ON DELETE CASCADE,
    CONSTRAINT chk_failure_count CHECK (failure_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ─────────────── Review cases (Test Run Review page) ───────────────

-- One scenario examined in depth, with the judge's decision and review progress.
CREATE TABLE review_case (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)   NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    category        VARCHAR(80)   NOT NULL,
    model           VARCHAR(80)   NOT NULL,
    started_at      DATETIME(6)   NOT NULL,
    prompt          VARCHAR(2000) NOT NULL,
    overall_score   INT           NOT NULL,
    run_status      VARCHAR(20)   NOT NULL,
    review_status   VARCHAR(20)   NOT NULL,
    verification    VARCHAR(20)   NOT NULL,
    pipeline_step   INT           NOT NULL,
    judge_decision  VARCHAR(80)   NOT NULL,
    judge_reason    VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_case_code UNIQUE (code),
    INDEX idx_review_case_status (review_status),
    CONSTRAINT chk_review_case_step CHECK (pipeline_step BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The assistant's answer in each language, with an English translation for TA/HI.
CREATE TABLE review_case_response (
    review_case_id  BIGINT        NOT NULL,
    position        INT           NOT NULL,
    language        VARCHAR(8)    NOT NULL,
    verdict         VARCHAR(10)   NOT NULL,
    response_text   VARCHAR(4000) NOT NULL,
    translation     VARCHAR(4000) NULL,
    PRIMARY KEY (review_case_id, position),
    CONSTRAINT fk_response_case FOREIGN KEY (review_case_id) REFERENCES review_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The judge's score on each measure (groundedness, harmfulness, language quality, safety).
CREATE TABLE review_case_score (
    review_case_id  BIGINT      NOT NULL,
    position        INT         NOT NULL,
    metric_key      VARCHAR(32) NOT NULL,
    label           VARCHAR(64) NOT NULL,
    score_value     DOUBLE      NOT NULL,
    rating          VARCHAR(32) NOT NULL,
    tone            VARCHAR(16) NOT NULL,
    PRIMARY KEY (review_case_id, position),
    CONSTRAINT fk_score_case FOREIGN KEY (review_case_id) REFERENCES review_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Observations from the investigator agent (passed = green tick, otherwise a warning).
CREATE TABLE review_case_finding (
    review_case_id  BIGINT        NOT NULL,
    position        INT           NOT NULL,
    passed          BIT(1)        NOT NULL,
    finding_text    VARCHAR(1000) NOT NULL,
    PRIMARY KEY (review_case_id, position),
    CONSTRAINT fk_finding_case FOREIGN KEY (review_case_id) REFERENCES review_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Trusted sources cited as evidence (e.g. Heart & Stroke, Health Canada).
CREATE TABLE review_case_source (
    review_case_id  BIGINT       NOT NULL,
    position        INT          NOT NULL,
    organization    VARCHAR(120) NOT NULL,
    short_name      VARCHAR(16)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    url             VARCHAR(500) NOT NULL,
    PRIMARY KEY (review_case_id, position),
    CONSTRAINT fk_source_case FOREIGN KEY (review_case_id) REFERENCES review_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ─────────────── Human decisions ───────────────

-- Every Approve / Request Fix / Retest decision. Rows are never overwritten (audit trail).
CREATE TABLE human_review (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    review_case_id    BIGINT       NOT NULL,
    safety            VARCHAR(40)  NOT NULL,
    groundedness      VARCHAR(40)  NOT NULL,
    language_quality  VARCHAR(40)  NOT NULL,
    verdict           VARCHAR(40)  NOT NULL,
    comment           VARCHAR(500) NOT NULL,
    action            VARCHAR(20)  NOT NULL,
    reviewer          VARCHAR(100) NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_human_review_case FOREIGN KEY (review_case_id) REFERENCES review_case (id),
    INDEX idx_human_review_case_created (review_case_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
