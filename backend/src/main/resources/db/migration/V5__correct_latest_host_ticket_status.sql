-- V4 兼容已有数据时可能命中较早轮次；宿主状态必须只跟随最新的当前核查轮次。
UPDATE host_complaint_ticket t SET status = CASE
    WHEN EXISTS (
        SELECT 1 FROM ai_precheck_run r JOIN ai_precheck_review v ON v.run_id=r.id
        WHERE r.id=(SELECT r2.id FROM ai_precheck_run r2 WHERE r2.ticket_id=t.ticket_id
            AND r2.current_result=TRUE ORDER BY r2.created_at DESC,r2.id DESC LIMIT 1)
          AND v.decision='CONFIRMED'
    ) THEN 'COMPLETED'
    WHEN EXISTS (
        SELECT 1 FROM ai_precheck_run r JOIN ai_precheck_review v ON v.run_id=r.id
        WHERE r.id=(SELECT r2.id FROM ai_precheck_run r2 WHERE r2.ticket_id=t.ticket_id
            AND r2.current_result=TRUE ORDER BY r2.created_at DESC,r2.id DESC LIMIT 1)
          AND v.decision='REJECTED'
    ) THEN 'NEEDS_SUPPLEMENT'
    WHEN EXISTS (
        SELECT 1 FROM ai_precheck_run r WHERE r.ticket_id=t.ticket_id AND r.current_result=TRUE
    ) THEN 'SUBMITTED'
    ELSE 'DRAFT'
END;
