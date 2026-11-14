-- Live test runs: real questions sent to the assistant under test, with every answer recorded.

-- Distinguish simulated sample runs from live runs, and record the model and finish time.
ALTER TABLE test_run
    ADD COLUMN source      VARCHAR(20) NOT NULL DEFAULT 'SAMPLE',
    ADD COLUMN model       VARCHAR(80) NULL,
    ADD COLUMN finished_at DATETIME(6) NULL,
    ADD INDEX idx_test_run_source_status (source, status);

-- One test scenario, e.g. "Chest pain that feels like gas". URGENT scenarios need a 9-1-1
-- instruction; CONTROL scenarios check the assistant does not escalate needlessly.
CREATE TABLE test_scenario (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    code                VARCHAR(16)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    category            VARCHAR(80)  NOT NULL,
    kind                VARCHAR(10)  NOT NULL,
    expected_behaviour  VARCHAR(500) NOT NULL,
    position            INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_test_scenario_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The scenario's question written in each language (native-speaker reviewed).
CREATE TABLE test_scenario_question (
    test_scenario_id  BIGINT        NOT NULL,
    position          INT           NOT NULL,
    language          VARCHAR(8)    NOT NULL,
    question_text     VARCHAR(2000) NOT NULL,
    PRIMARY KEY (test_scenario_id, position),
    CONSTRAINT fk_scenario_question FOREIGN KEY (test_scenario_id) REFERENCES test_scenario (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One question asked in one language during a live run: the exact question, model, time and answer.
CREATE TABLE test_result (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    test_run_id       BIGINT         NOT NULL,
    test_scenario_id  BIGINT         NOT NULL,
    language          VARCHAR(8)     NOT NULL,
    question_text     VARCHAR(2000)  NOT NULL,
    answer_text       VARCHAR(10000) NULL,
    model             VARCHAR(80)    NOT NULL,
    verdict           VARCHAR(10)    NOT NULL,
    check_detail      VARCHAR(500)   NOT NULL,
    duration_ms       BIGINT         NOT NULL,
    asked_at          DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_result_run FOREIGN KEY (test_run_id) REFERENCES test_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_result_scenario FOREIGN KEY (test_scenario_id) REFERENCES test_scenario (id),
    INDEX idx_test_result_run (test_run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
