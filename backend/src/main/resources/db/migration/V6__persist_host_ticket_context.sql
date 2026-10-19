ALTER TABLE host_complaint_ticket
    ADD COLUMN customer_ref VARCHAR(64) NULL,
    ADD COLUMN complaint VARCHAR(1000) NULL,
    ADD COLUMN category VARCHAR(64) NULL,
    ADD COLUMN channel VARCHAR(32) NULL,
    ADD COLUMN synthetic BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN source_system VARCHAR(64) NOT NULL DEFAULT 'mysql-host',
    ADD COLUMN created_by VARCHAR(64) NOT NULL DEFAULT 'system:migration',
    ADD COLUMN updated_by VARCHAR(64) NOT NULL DEFAULT 'system:migration',
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

UPDATE host_complaint_ticket SET
    customer_ref=CONCAT('SYNTHETIC-CUSTOMER-',SUBSTRING(ticket_id,6)),
    complaint='这个月扣了30元视频会员费，我不记得主动办理过，请帮我核实。',
    category='增值业务扣费争议',channel='10000号热线';

ALTER TABLE host_complaint_ticket
    MODIFY customer_ref VARCHAR(64) NOT NULL,
    MODIFY complaint VARCHAR(1000) NOT NULL,
    MODIFY category VARCHAR(64) NOT NULL,
    MODIFY channel VARCHAR(32) NOT NULL;

CREATE TABLE host_bill_evidence (
    evidence_id VARCHAR(64) PRIMARY KEY,
    ticket_id VARCHAR(64) NOT NULL,
    service_id VARCHAR(64) NOT NULL,
    subscription_id VARCHAR(64) NOT NULL,
    billing_period CHAR(7) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    INDEX idx_host_bill_ticket(ticket_id),
    CONSTRAINT fk_host_bill_ticket FOREIGN KEY(ticket_id) REFERENCES host_complaint_ticket(ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE host_subscription_evidence (
    evidence_id VARCHAR(64) PRIMARY KEY,
    ticket_id VARCHAR(64) NOT NULL,
    subscription_id VARCHAR(64) NOT NULL,
    service_id VARCHAR(64) NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    monthly_price DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    ordered_at TIMESTAMP(6) NOT NULL,
    INDEX idx_host_subscription_ticket(ticket_id),
    CONSTRAINT fk_host_subscription_ticket FOREIGN KEY(ticket_id) REFERENCES host_complaint_ticket(ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE host_authorization_evidence (
    evidence_id VARCHAR(64) PRIMARY KEY,
    ticket_id VARCHAR(64) NOT NULL,
    subscription_id VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    authorized_at TIMESTAMP(6) NOT NULL,
    description VARCHAR(256) NOT NULL,
    INDEX idx_host_authorization_ticket(ticket_id),
    CONSTRAINT fk_host_authorization_ticket FOREIGN KEY(ticket_id) REFERENCES host_complaint_ticket(ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO host_bill_evidence VALUES
('BILL-001','DEMO-001','VIDEO','SUB-001','2026-10',30.00,'CNY'),
('BILL-002','DEMO-002','VIDEO','SUB-002','2026-10',30.00,'CNY'),
('BILL-003','DEMO-003','VIDEO','SUB-003','2026-10',30.00,'CNY');

INSERT INTO host_subscription_evidence VALUES
('ORDER-001','DEMO-001','SUB-001','VIDEO','视频会员',30.00,'CNY','2026-09-01 02:00:00'),
('ORDER-002','DEMO-002','SUB-002','VIDEO','视频会员',30.00,'CNY','2026-09-01 02:00:00'),
('ORDER-003','DEMO-003','SUB-003','VIDEO','视频会员',20.00,'CNY','2026-09-01 02:00:00');

INSERT INTO host_authorization_evidence VALUES
('AUTH-001','DEMO-001','SUB-001','MOCK_SMS','2026-09-01 02:00:00','合成短信确认记录，仅用于演示'),
('AUTH-003','DEMO-003','SUB-003','MOCK_SMS','2026-09-01 02:00:00','合成短信确认记录，仅用于演示');
