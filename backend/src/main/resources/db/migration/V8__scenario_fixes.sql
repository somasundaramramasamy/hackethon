-- Fixes learned from failures, one per scenario and language. When an answer fails, the judge writes
-- what the assistant should do differently; the next run of that scenario in that language gives the
-- assistant that guidance with the question. A person can turn a fix off.
CREATE TABLE scenario_fix (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    test_scenario_id  BIGINT        NOT NULL,
    language          VARCHAR(8)    NOT NULL,
    fix_text          MEDIUMTEXT    NOT NULL,
    failure_reason    MEDIUMTEXT    NULL,
    source_result_id  BIGINT        NULL,
    source_run_id     BIGINT        NULL,
    revision          INT           NOT NULL DEFAULT 1,
    active            BIT(1)        NOT NULL DEFAULT b'1',
    times_used        INT           NOT NULL DEFAULT 0,
    times_passed      INT           NOT NULL DEFAULT 0,
    last_outcome      VARCHAR(16)   NULL,
    last_used_at      DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    changed_by        VARCHAR(100)  NULL,
    change_note       VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_scenario_fix UNIQUE (test_scenario_id, language),
    CONSTRAINT fk_scenario_fix_scenario FOREIGN KEY (test_scenario_id) REFERENCES test_scenario (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- What the judge said to do differently, and which saved fix (if any) the assistant was given.
ALTER TABLE test_result
    ADD COLUMN judge_fix        MEDIUMTEXT NULL,
    ADD COLUMN applied_fix_id   BIGINT     NULL,
    ADD COLUMN applied_fix_text MEDIUMTEXT NULL;
