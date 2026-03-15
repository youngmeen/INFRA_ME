CREATE TABLE IF NOT EXISTS news (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    source VARCHAR(255) NOT NULL,
    link TEXT NOT NULL,
    published_at BIGINT NOT NULL,
    score DOUBLE NOT NULL,
    created_at BIGINT NOT NULL,
    link_hash VARCHAR(191) NOT NULL,
    UNIQUE KEY uq_news_link_hash (link_hash),
    INDEX idx_news_published_at (published_at),
    INDEX idx_news_category (category),
    INDEX idx_news_score (score)
);

CREATE TABLE IF NOT EXISTS send_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    send_type VARCHAR(64) NOT NULL,
    send_date DATE NOT NULL,
    sent_at BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    sent_count INT NOT NULL DEFAULT 0,
    message VARCHAR(512),
    digest_type VARCHAR(64),
    digest_date DATE,
    UNIQUE KEY uq_send_history_date_type (send_date, send_type),
    INDEX idx_send_history_status (status)
);
