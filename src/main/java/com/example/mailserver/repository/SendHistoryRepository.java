package com.example.mailserver.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class SendHistoryRepository {

    private static final String DAILY_TYPE = "DAILY_NEWS";
    private static final String LEGACY_DAILY_TYPE = "DAILY";

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS send_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    digest_date TEXT NOT NULL,
                    digest_type TEXT NOT NULL,
                    sent_at INTEGER NOT NULL,
                    sent_count INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    message TEXT,
                    UNIQUE(digest_date, digest_type)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_send_history_date_type ON send_history(digest_date, digest_type)");
    }

    public boolean claimDailyIfAbsent(LocalDate date) {
        Integer existing = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM send_history
                        WHERE digest_date = ?
                          AND digest_type IN (?, ?)
                        """,
                Integer.class,
                date.toString(),
                DAILY_TYPE,
                LEGACY_DAILY_TYPE
        );
        if (existing != null && existing > 0) {
            return false;
        }

        int changed = jdbcTemplate.update("""
                        INSERT INTO send_history (digest_date, digest_type, sent_at, sent_count, status, message)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(digest_date, digest_type) DO NOTHING
                        """,
                date.toString(),
                DAILY_TYPE,
                Instant.now().toEpochMilli(),
                0,
                "PENDING",
                "startup-check"
        );
        return changed > 0;
    }

    public void upsertDailyResult(LocalDate date, int sentCount, String status, String message) {
        jdbcTemplate.update("""
                        INSERT INTO send_history (digest_date, digest_type, sent_at, sent_count, status, message)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(digest_date, digest_type) DO UPDATE SET
                          sent_at = excluded.sent_at,
                          sent_count = excluded.sent_count,
                          status = excluded.status,
                          message = excluded.message
                        """,
                date.toString(),
                DAILY_TYPE,
                Instant.now().toEpochMilli(),
                Math.max(0, sentCount),
                status,
                message
        );
    }

    public void completeDaily(LocalDate date, int sentCount, String status, String message) {
        jdbcTemplate.update("""
                        UPDATE send_history
                        SET sent_at = ?, sent_count = ?, status = ?, message = ?
                        WHERE digest_date = ? AND digest_type IN (?, ?)
                        """,
                Instant.now().toEpochMilli(),
                Math.max(0, sentCount),
                status,
                message,
                date.toString(),
                DAILY_TYPE,
                LEGACY_DAILY_TYPE
        );
    }
}
