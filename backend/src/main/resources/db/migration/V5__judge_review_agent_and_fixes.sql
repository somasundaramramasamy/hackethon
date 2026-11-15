-- Real results only: the AI judge, human review, the investigator agent and approved prompt fixes.
-- Written for MySQL and TiDB (TiDB cannot index a column added in the same ALTER TABLE).

-- ── 1. Remove the simulated sample data and its tables ──
DROP TABLE human_review;
DROP TABLE review_case_response;
DROP TABLE review_case_score;
DROP TABLE review_case_finding;
DROP TABLE review_case_source;
DROP TABLE review_case;

-- Per-language totals and failure counts are now computed from the results themselves.
DROP TABLE test_run_language_result;
DROP TABLE test_run_failure;

-- ── 2. Start from a clean slate ──
-- Earlier runs used another model setup and the six draft scenarios; the 15-scenario test set
-- is loaded from scenarios/default-scenarios.json on the next start.
DELETE FROM test_result;
DELETE FROM test_run;
DELETE FROM test_scenario_question;
DELETE FROM test_scenario_must_do;
DELETE FROM test_scenario_must_not_do;
DELETE FROM test_scenario;

-- ── 3. Scenarios: which objective signals the rule check looks for ──
-- Comma-separated, e.g. EMERGENCY_NUMBER or POISON_CENTRE,EMERGENCY_NUMBER. Empty = none.
ALTER TABLE test_scenario
    ADD COLUMN rule_signals VARCHAR(200) NULL;

-- ── 4. Versions of the assistant's system prompt ──
-- A fix is a new version: proposed by the agent, approved or rejected by a named person, retested,
-- and only then adopted. Exactly one version is ACTIVE at a time (enforced by the application).
CREATE TABLE assistant_prompt (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    version           INT           NOT NULL,
    prompt_text       MEDIUMTEXT    NOT NULL,
    status            VARCHAR(16)   NOT NULL,
    summary           VARCHAR(1000) NULL,
    rationale         MEDIUMTEXT    NULL,
    based_on_version  INT           NULL,
    source_run_id     BIGINT        NULL,
    created_by        VARCHAR(100)  NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    decided_by        VARCHAR(100)  NULL,
    decided_at        DATETIME(6)   NULL,
    decision_note     VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_assistant_prompt_version UNIQUE (version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ── 5. Runs: which judge and prompt version they used, and what a retest compares against ──
ALTER TABLE test_run
    DROP INDEX idx_test_run_source_status;

ALTER TABLE test_run
    DROP COLUMN source;

ALTER TABLE test_run
    MODIFY model VARCHAR(120) NULL;

ALTER TABLE test_run
    ADD COLUMN judge_model     VARCHAR(120) NULL,
    ADD COLUMN prompt_version  INT          NULL,
    ADD COLUMN run_type        VARCHAR(16)  NOT NULL DEFAULT 'BASELINE',
    ADD COLUMN baseline_run_id BIGINT       NULL,
    ADD COLUMN repeats         INT          NOT NULL DEFAULT 1,
    ADD COLUMN planned_tests   INT          NOT NULL DEFAULT 0,
    ADD COLUMN started_by      VARCHAR(100) NULL;

ALTER TABLE test_run
    ADD INDEX idx_test_run_status (status);

-- ── 6. Results: rule check, judge verdict with evidence, translation and human review ──
-- verdict is the combined verdict: PASS, FAIL, NEEDS_REVIEW or ERROR.
ALTER TABLE test_result
    MODIFY verdict VARCHAR(16) NOT NULL;

ALTER TABLE test_result
    MODIFY model VARCHAR(120) NOT NULL;

ALTER TABLE test_result
    ADD COLUMN attempt          INT          NOT NULL DEFAULT 1,
    ADD COLUMN rule_verdict     VARCHAR(16)  NULL,
    ADD COLUMN rule_detail      VARCHAR(500) NULL,
    ADD COLUMN judge_verdict    VARCHAR(16)  NULL,
    ADD COLUMN judge_confidence DOUBLE       NULL,
    ADD COLUMN judge_reason     MEDIUMTEXT   NULL,
    ADD COLUMN judge_criteria   MEDIUMTEXT   NULL,
    ADD COLUMN translation      MEDIUMTEXT   NULL,
    ADD COLUMN audit_sample     BIT(1)       NOT NULL DEFAULT b'0',
    ADD COLUMN human_verdict    VARCHAR(16)  NULL,
    ADD COLUMN reviewed_by      VARCHAR(100) NULL,
    ADD COLUMN reviewed_at      DATETIME(6)  NULL,
    ADD COLUMN review_note      MEDIUMTEXT   NULL;

ALTER TABLE test_result
    ADD INDEX idx_test_result_queue (verdict, human_verdict);

-- ── 7. The investigator agent's work on one result, with its step-by-step decision log ──
CREATE TABLE investigation (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    test_result_id  BIGINT        NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    agent_model     VARCHAR(120)  NOT NULL,
    conclusion      VARCHAR(24)   NULL,
    failure_type    VARCHAR(16)   NULL,
    diagnosis       MEDIUMTEXT    NULL,
    suggested_fix   MEDIUMTEXT    NULL,
    steps           MEDIUMTEXT    NULL,
    error           VARCHAR(500)  NULL,
    started_by      VARCHAR(100)  NULL,
    started_at      DATETIME(6)   NOT NULL,
    finished_at     DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_investigation_result FOREIGN KEY (test_result_id) REFERENCES test_result (id) ON DELETE CASCADE,
    INDEX idx_investigation_result (test_result_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
