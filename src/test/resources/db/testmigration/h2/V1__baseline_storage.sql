CREATE TABLE news (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title CLOB NOT NULL,
    summary CLOB NOT NULL,
    category VARCHAR(32) NOT NULL,
    source VARCHAR(255) NOT NULL,
    link CLOB NOT NULL,
    published_at BIGINT NOT NULL,
    score DOUBLE NOT NULL,
    created_at BIGINT NOT NULL,
    link_hash VARCHAR(191) NOT NULL,
    CONSTRAINT uq_news_link_hash UNIQUE (link_hash)
);

CREATE INDEX idx_news_published_at ON news (published_at);
CREATE INDEX idx_news_category ON news (category);
CREATE INDEX idx_news_score ON news (score);

CREATE TABLE send_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    send_type VARCHAR(64) NOT NULL,
    send_date DATE NOT NULL,
    sent_at BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    sent_count INT NOT NULL DEFAULT 0,
    message VARCHAR(512),
    digest_type VARCHAR(64),
    digest_date DATE,
    CONSTRAINT uq_send_history_date_type UNIQUE (send_date, send_type)
);

CREATE INDEX idx_send_history_status ON send_history (status);
