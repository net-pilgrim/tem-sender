-- Multitenant Mail Sender Database Schema (Operator Mode)

CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    daily_quota INT NOT NULL DEFAULT 10000,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS mail_domains (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    domain_name VARCHAR(255) NOT NULL UNIQUE,
    scaleway_domain_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    spf_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    dkim_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    dmarc_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    mx_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    dns_records_json CLOB NOT NULL,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_domain_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS queued_emails (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    domain_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(255),
    from_email VARCHAR(255) NOT NULL,
    from_name VARCHAR(255),
    to_emails_json CLOB NOT NULL,
    cc_emails_json CLOB,
    bcc_emails_json CLOB,
    subject VARCHAR(500) NOT NULL,
    text_body CLOB,
    html_body CLOB,
    attachments_json CLOB,
    headers_json CLOB,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    scaleway_message_id VARCHAR(255),
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error CLOB,
    error_history_json CLOB,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_email_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_email_domain FOREIGN KEY (domain_id) REFERENCES mail_domains(id)
);

CREATE INDEX IF NOT EXISTS idx_emails_tenant_id ON queued_emails(tenant_id);
CREATE INDEX IF NOT EXISTS idx_emails_domain_id ON queued_emails(domain_id);
CREATE INDEX IF NOT EXISTS idx_emails_queue ON queued_emails(status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_emails_created_at ON queued_emails(created_at);
CREATE INDEX IF NOT EXISTS idx_domains_tenant_id ON mail_domains(tenant_id);
