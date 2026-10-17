CREATE TABLE ai_precheck_run (
    id CHAR(36) PRIMARY KEY,
    ticket_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    call_status VARCHAR(32) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    requested_model VARCHAR(128) NOT NULL,
    actual_model VARCHAR(128),
    trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    duration_ms BIGINT,
    failure_code VARCHAR(64),
    result_json JSON,
    findings_json JSON,
    version INT NOT NULL DEFAULT 0,
    INDEX idx_run_ticket_created (ticket_id, created_at),
    INDEX idx_run_recovery (status, created_at),
    CONSTRAINT chk_run_status CHECK (status IN ('ANALYZING','PENDING_REVIEW')),
    CONSTRAINT chk_run_call CHECK (call_status IN ('RUNNING','SUCCESS','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_precheck_evidence_snapshot (
    run_id CHAR(36) PRIMARY KEY,
    context_json JSON NOT NULL,
    CONSTRAINT fk_snapshot_run FOREIGN KEY (run_id) REFERENCES ai_precheck_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
