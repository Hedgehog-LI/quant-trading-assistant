-- ============================================================
-- V16: agent_api_audit_log — Agent 只读 API 调用脱敏审计
-- 不修改 V1-V15
-- ============================================================

CREATE TABLE agent_api_audit_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id      VARCHAR(64)     NOT NULL,
    client_id       VARCHAR(128)    NOT NULL,
    sender_hash     VARCHAR(64),
    operation_code  VARCHAR(64)     NOT NULL,
    method          VARCHAR(8)      NOT NULL,
    path            VARCHAR(256)    NOT NULL,
    param_summary   VARCHAR(512),
    http_status     INT             NOT NULL,
    error_code      VARCHAR(64),
    result_count    INT             DEFAULT 0,
    duration_ms     BIGINT          NOT NULL DEFAULT 0,
    requested_at    DATETIME        NOT NULL,
    completed_at    DATETIME        NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_audit_request_id ON agent_api_audit_log (request_id);
CREATE INDEX idx_agent_audit_client ON agent_api_audit_log (client_id);
CREATE INDEX idx_agent_audit_requested_at ON agent_api_audit_log (requested_at);
