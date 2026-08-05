CREATE TABLE IF NOT EXISTS user_feedback (
    id UUID PRIMARY KEY,
    feedback_no VARCHAR(32) NOT NULL UNIQUE,
    user_id UUID,
    lookup_code_hash CHAR(64) NOT NULL,
    category_id VARCHAR(32) NOT NULL,
    title VARCHAR(80) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    environment VARCHAR(200),
    status VARCHAR(24) NOT NULL,
    reply VARCHAR(4000),
    replied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_feedback_number_check CHECK (feedback_no ~ '^FB-[0-9]{8}-[A-F0-9]{12}$'),
    CONSTRAINT user_feedback_category_check CHECK (category_id IN (
        'quick-start',
        'account-login',
        'ai-training',
        'audio',
        'learning-records',
        'membership',
        'privacy-security',
        'feedback'
    )),
    CONSTRAINT user_feedback_status_check CHECK (status IN (
        'SUBMITTED',
        'IN_PROGRESS',
        'RESOLVED',
        'CLOSED'
    )),
    CONSTRAINT user_feedback_title_check CHECK (BTRIM(title) <> ''),
    CONSTRAINT user_feedback_description_check CHECK (BTRIM(description) <> ''),
    CONSTRAINT user_feedback_reply_time_check CHECK (
        (reply IS NULL AND replied_at IS NULL)
        OR (reply IS NOT NULL AND BTRIM(reply) <> '' AND replied_at IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_user_feedback_user_created
ON user_feedback (user_id, created_at DESC)
WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_feedback_status_updated
ON user_feedback (status, updated_at DESC);

COMMENT ON TABLE user_feedback IS
'帮助中心用户反馈、处理状态及工作人员回复';

COMMENT ON COLUMN user_feedback.user_id IS
'登录用户的逻辑关联 user.id；匿名反馈为空，不设置数据库外键';

COMMENT ON COLUMN user_feedback.lookup_code_hash IS
'匿名查询码的 SHA-256 摘要，不保存查询码明文';
