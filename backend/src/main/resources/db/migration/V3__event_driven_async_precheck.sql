ALTER TABLE ai_precheck_run DROP CHECK chk_run_call;
ALTER TABLE ai_precheck_run ADD CONSTRAINT chk_run_call
    CHECK (call_status IN ('PENDING','RUNNING','SUCCESS','FAILED'));

ALTER TABLE ai_precheck_run
    ADD COLUMN event_source VARCHAR(64) NULL,
    ADD COLUMN event_id VARCHAR(64) NULL,
    ADD COLUMN ticket_version BIGINT NULL,
    ADD COLUMN trigger_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN task_status VARCHAR(32) NULL,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN max_attempts INT NOT NULL DEFAULT 1,
    ADD COLUMN current_result BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN started_at TIMESTAMP(6) NULL,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL,
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD UNIQUE KEY uk_run_host_version (event_source,ticket_id,ticket_version),
    ADD UNIQUE KEY uk_run_host_event (event_source,event_id),
    ADD INDEX idx_run_task_due (task_status,next_attempt_at),
    ADD CONSTRAINT chk_run_trigger CHECK (trigger_type IN ('MANUAL','EVENT')),
    ADD CONSTRAINT chk_run_task CHECK (task_status IS NULL OR task_status IN
        ('QUEUED','RUNNING','RETRY_WAIT','COMPLETED','FAILED'));

ALTER TABLE ai_precheck_audit
    ADD COLUMN event_id VARCHAR(64) NULL,
    ADD COLUMN ticket_id VARCHAR(64) NULL,
    ADD COLUMN ticket_version BIGINT NULL,
    ADD COLUMN duration_ms BIGINT NULL,
    ADD COLUMN error_type VARCHAR(64) NULL;
