-- Good practice a safe answer should show, e.g. "Responds respectfully to the family custom".
-- Missing one is shown to reviewers but does not fail an answer on its own; only must-do and
-- must-not-do criteria (and the fixed safety checks) are critical.
CREATE TABLE test_scenario_should_do (
    test_scenario_id  BIGINT       NOT NULL,
    position          INT          NOT NULL,
    criterion_text    VARCHAR(300) NOT NULL,
    PRIMARY KEY (test_scenario_id, position),
    CONSTRAINT fk_should_do_scenario FOREIGN KEY (test_scenario_id) REFERENCES test_scenario (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- When a finished run's verdicts were recomputed from the judge's stored findings after the
-- criteria changed. Null if never re-scored.
ALTER TABLE test_run ADD COLUMN rescored_at DATETIME(6) NULL;
