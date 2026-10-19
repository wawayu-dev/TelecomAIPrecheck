CREATE TABLE host_complaint_ticket (
    ticket_id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    note VARCHAR(256) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    event_id VARCHAR(64),
    ticket_version BIGINT,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_host_ticket_status CHECK (status IN
        ('DRAFT','SUBMITTED','NEEDS_SUPPLEMENT','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO host_complaint_ticket(ticket_id,title,note,priority,status) VALUES
('DEMO-001','视频会员扣费争议','账单与授权材料齐全','普通','DRAFT'),
('DEMO-002','未确认的业务订购','缺少授权凭据','关注','DRAFT'),
('DEMO-003','月费金额不一致','账单与订购价格存在差异','普通','DRAFT');

-- 升级已有演示库时，根据已持久化的当前核查/人工结果恢复宿主工单状态。
UPDATE host_complaint_ticket t SET status = CASE
    WHEN EXISTS (
        SELECT 1 FROM ai_precheck_run r JOIN ai_precheck_review v ON v.run_id=r.id
        WHERE r.ticket_id=t.ticket_id AND r.current_result=TRUE AND v.decision='CONFIRMED'
    ) THEN 'COMPLETED'
    WHEN EXISTS (
        SELECT 1 FROM ai_precheck_run r JOIN ai_precheck_review v ON v.run_id=r.id
        WHERE r.ticket_id=t.ticket_id AND r.current_result=TRUE AND v.decision='REJECTED'
    ) THEN 'NEEDS_SUPPLEMENT'
    WHEN EXISTS (
        SELECT 1 FROM ai_precheck_run r WHERE r.ticket_id=t.ticket_id AND r.current_result=TRUE
    ) THEN 'SUBMITTED'
    ELSE 'DRAFT'
END;
