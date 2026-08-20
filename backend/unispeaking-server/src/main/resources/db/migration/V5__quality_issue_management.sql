-- Persist actionable quality events without changing existing user data.
CREATE TABLE quality_issues (
    issue_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fingerprint VARCHAR(64),
    issue_type VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    title VARCHAR(200) NOT NULL,
    description TEXT,
    error_code VARCHAR(120),
    api_path VARCHAR(300),
    http_status INTEGER,
    release VARCHAR(100),
    assignee VARCHAR(120),
    resolution TEXT,
    occurrence_count BIGINT NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_by VARCHAR(120),
    updated_by VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quality_issue_type_check CHECK (issue_type IN ('BUG', 'OPTIMIZATION')),
    CONSTRAINT quality_issue_source_check CHECK (source IN ('TELEMETRY', 'MANUAL')),
    CONSTRAINT quality_issue_platform_check CHECK (platform IN ('WEB', 'MOBILE', 'BACKEND', 'CROSS_PLATFORM')),
    CONSTRAINT quality_issue_severity_check CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT quality_issue_status_check CHECK (status IN ('OPEN', 'INVESTIGATING', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'IGNORED')),
    CONSTRAINT quality_issue_occurrence_count_check CHECK (occurrence_count >= 0),
    CONSTRAINT quality_issue_http_status_check CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599)
);

CREATE UNIQUE INDEX uq_quality_issues_fingerprint
    ON quality_issues (fingerprint) WHERE fingerprint IS NOT NULL;
CREATE INDEX idx_quality_issues_status_severity
    ON quality_issues (status, severity, last_seen_at DESC);
CREATE INDEX idx_quality_issues_platform_last_seen
    ON quality_issues (platform, last_seen_at DESC);

CREATE TABLE quality_issue_events (
    event_id VARCHAR(80) PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES quality_issues(issue_id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    anonymous_id VARCHAR(80),
    session_id VARCHAR(100),
    platform VARCHAR(20) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    release VARCHAR(100),
    route VARCHAR(300),
    message VARCHAR(500),
    stack TEXT,
    api_path VARCHAR(300),
    api_method VARCHAR(16),
    http_status INTEGER,
    outcome VARCHAR(40),
    error_code VARCHAR(120),
    error_name VARCHAR(160),
    device_model VARCHAR(160),
    os_name VARCHAR(80),
    os_version VARCHAR(80),
    network_type VARCHAR(80),
    duration_ms DOUBLE PRECISION,
    attributes JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quality_event_platform_check CHECK (platform IN ('WEB', 'MOBILE', 'BACKEND')),
    CONSTRAINT quality_event_severity_check CHECK (severity IN ('INFO', 'WARN', 'ERROR', 'FATAL')),
    CONSTRAINT quality_event_http_status_check CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599)
);

CREATE INDEX idx_quality_issue_events_issue_time
    ON quality_issue_events (issue_id, occurred_at DESC);
CREATE INDEX idx_quality_issue_events_user_time
    ON quality_issue_events (user_id, occurred_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX idx_quality_issue_events_platform_time
    ON quality_issue_events (platform, occurred_at DESC);

CREATE TABLE quality_issue_history (
    history_id BIGSERIAL PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES quality_issues(issue_id) ON DELETE CASCADE,
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24),
    note TEXT,
    actor_admin_id UUID REFERENCES admin_accounts(id) ON DELETE SET NULL,
    actor_login VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quality_issue_history_issue_time
    ON quality_issue_history (issue_id, created_at DESC);
