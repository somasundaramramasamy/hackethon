-- A name people give a test run, e.g. "Before launch - full check". Optional: older runs and runs
-- started without one fall back to a name built from the model and settings.
ALTER TABLE test_run ADD COLUMN name VARCHAR(120) NULL;
