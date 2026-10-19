ALTER TABLE ai_precheck_review DROP CHECK chk_review_decision;
ALTER TABLE ai_precheck_review
    ADD COLUMN evidence_conclusion VARCHAR(20) NOT NULL DEFAULT 'INSUFFICIENT' AFTER decision,
    ADD COLUMN next_action VARCHAR(24) NOT NULL DEFAULT 'REQUEST_EVIDENCE' AFTER evidence_conclusion,
    ADD CONSTRAINT chk_review_decision CHECK (decision IN ('CONFIRMED','REJECTED','ESCALATED')),
    ADD CONSTRAINT chk_evidence_conclusion CHECK (evidence_conclusion IN ('SUFFICIENT','INSUFFICIENT','CONFLICT')),
    ADD CONSTRAINT chk_review_next_action CHECK (next_action IN ('REQUEST_EVIDENCE','SUBMIT_CONCLUSION','ESCALATE'));

UPDATE ai_precheck_review
SET evidence_conclusion = CASE WHEN decision='CONFIRMED' THEN 'SUFFICIENT' ELSE 'INSUFFICIENT' END,
    next_action = CASE WHEN decision='CONFIRMED' THEN 'SUBMIT_CONCLUSION' ELSE 'REQUEST_EVIDENCE' END;

ALTER TABLE host_complaint_ticket DROP CHECK chk_host_ticket_status;
ALTER TABLE host_complaint_ticket ADD CONSTRAINT chk_host_ticket_status CHECK (status IN
    ('DRAFT','SUBMITTED','NEEDS_SUPPLEMENT','NEEDS_REVIEW','COMPLETED'));
