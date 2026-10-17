ALTER TABLE ai_precheck_run DROP CHECK chk_run_status;
ALTER TABLE ai_precheck_run ADD CONSTRAINT chk_run_status CHECK (status IN ('ANALYZING','PENDING_REVIEW','REVIEWED'));

CREATE TABLE ai_precheck_review (
    run_id CHAR(36) PRIMARY KEY,
    decision VARCHAR(16) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    recommendation VARCHAR(32) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    reviewed_at TIMESTAMP(6) NOT NULL,
    version INT NOT NULL,
    CONSTRAINT fk_review_run FOREIGN KEY (run_id) REFERENCES ai_precheck_run(id),
    CONSTRAINT chk_review_decision CHECK (decision IN ('CONFIRMED','REJECTED')),
    CONSTRAINT chk_review_recommendation CHECK (recommendation IN ('VERIFY_AUTHORIZATION','VERIFY_BILLING','MANUAL_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_precheck_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id CHAR(36) NOT NULL,
    snapshot_run_id CHAR(36) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    run_version INT NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    INDEX idx_audit_run (run_id,id),
    CONSTRAINT fk_audit_run FOREIGN KEY (run_id) REFERENCES ai_precheck_run(id),
    CONSTRAINT fk_audit_snapshot FOREIGN KEY (snapshot_run_id) REFERENCES ai_precheck_evidence_snapshot(run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 升级前的历史只建立基线；不虚构历史操作人或操作时间。
INSERT INTO ai_precheck_audit
(run_id,snapshot_run_id,actor_id,action,from_status,to_status,outcome,run_version,prompt_version,model,trace_id,occurred_at)
SELECT id,id,'system:migration','BASELINE_IMPORT',NULL,status,'IMPORTED',version,prompt_version,
COALESCE(actual_model,requested_model),trace_id,UTC_TIMESTAMP(6) FROM ai_precheck_run;
