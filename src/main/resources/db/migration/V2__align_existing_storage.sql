ALTER TABLE news
    ADD COLUMN IF NOT EXISTS category VARCHAR(32) NOT NULL DEFAULT 'DEV';

ALTER TABLE news
    ADD COLUMN IF NOT EXISTS source VARCHAR(255) NOT NULL DEFAULT 'unknown';

ALTER TABLE news
    ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0;

ALTER TABLE news
    ADD COLUMN IF NOT EXISTS link_hash VARCHAR(191) NOT NULL DEFAULT '';

ALTER TABLE send_history
    ADD COLUMN IF NOT EXISTS send_type VARCHAR(64) NOT NULL DEFAULT 'DAILY_NEWS';

ALTER TABLE send_history
    ADD COLUMN IF NOT EXISTS send_date DATE NULL;

ALTER TABLE send_history
    ADD COLUMN IF NOT EXISTS sent_count INT NOT NULL DEFAULT 0;

ALTER TABLE send_history
    ADD COLUMN IF NOT EXISTS message VARCHAR(512);

ALTER TABLE send_history
    ADD COLUMN IF NOT EXISTS digest_type VARCHAR(64);

ALTER TABLE send_history
    ADD COLUMN IF NOT EXISTS digest_date DATE NULL;

UPDATE news
SET created_at = CASE
    WHEN created_at = 0 THEN published_at
    ELSE created_at
END;

UPDATE news
SET link_hash = CAST(id AS CHAR(32))
WHERE link_hash = '';

UPDATE send_history
SET send_type = COALESCE(NULLIF(send_type, ''), digest_type, 'DAILY_NEWS');

UPDATE send_history
SET digest_type = COALESCE(NULLIF(digest_type, ''), send_type);

UPDATE send_history
SET send_date = COALESCE(
    send_date,
    CASE
        WHEN digest_date IS NOT NULL THEN digest_date
        ELSE STR_TO_DATE(NULLIF(CAST(digest_date AS CHAR(16)), ''), '%Y-%m-%d')
    END
);

UPDATE send_history
SET digest_date = COALESCE(
    digest_date,
    send_date,
    STR_TO_DATE(NULLIF(CAST(send_date AS CHAR(16)), ''), '%Y-%m-%d')
);

ALTER TABLE send_history
    MODIFY COLUMN send_date DATE NOT NULL;

ALTER TABLE send_history
    MODIFY COLUMN digest_date DATE NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_news_link_hash ON news (link_hash);
CREATE INDEX IF NOT EXISTS idx_news_published_at ON news (published_at);
CREATE INDEX IF NOT EXISTS idx_news_category ON news (category);
CREATE INDEX IF NOT EXISTS idx_news_score ON news (score);
CREATE UNIQUE INDEX IF NOT EXISTS uq_send_history_date_type ON send_history (send_date, send_type);
CREATE INDEX IF NOT EXISTS idx_send_history_status ON send_history (status);
