-- Scenarios can be switched off without deleting them (past results still reference them).
-- Live runs only ask active scenarios.
ALTER TABLE test_scenario ADD COLUMN active BIT(1) NOT NULL DEFAULT b'1';
