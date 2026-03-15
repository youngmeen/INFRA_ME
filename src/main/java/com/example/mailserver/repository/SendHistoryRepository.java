package com.example.mailserver.repository;

import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class SendHistoryRepository {

    private static final String DAILY_TYPE = "DAILY_NEWS";
    private static final String LEGACY_DAILY_TYPE = "DAILY";
    private static final String CREATE_SEND_HISTORY_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS send_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "send_type TEXT NOT NULL,"
                    + "send_date TEXT NOT NULL,"
                    + "sent_at INTEGER NOT NULL,"
                    + "status TEXT NOT NULL,"
                    + "sent_count INTEGER NOT NULL DEFAULT 0,"
                    + "message TEXT,"
                    + "UNIQUE(send_date, send_type)"
                    + ")";
    private static final String HAS_DAILY_SENT_SQL =
            "SELECT COUNT(*) "
                    + "FROM send_history "
                    + "WHERE send_date = ? "
                    + "  AND send_type IN (?, ?) "
                    + "  AND UPPER(status) IN ('SUCCESS', 'SENT')";
    private static final String CLAIM_DAILY_SQL =
            "INSERT INTO send_history (send_date, send_type, digest_date, digest_type, sent_at, sent_count, status, message) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(send_date, send_type) DO UPDATE SET "
                    + "  sent_at = excluded.sent_at, "
                    + "  digest_date = excluded.digest_date, "
                    + "  digest_type = excluded.digest_type, "
                    + "  status = 'PENDING', "
                    + "  message = excluded.message "
                    + "WHERE UPPER(send_history.status) NOT IN ('SUCCESS', 'SENT', 'PENDING')";
    private static final String SAVE_DAILY_RESULT_SQL =
            "INSERT INTO send_history (send_date, send_type, digest_date, digest_type, sent_at, sent_count, status, message) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(send_date, send_type) DO UPDATE SET "
                    + "  digest_date = excluded.digest_date, "
                    + "  digest_type = excluded.digest_type, "
                    + "  sent_at = excluded.sent_at, "
                    + "  sent_count = excluded.sent_count, "
                    + "  status = excluded.status, "
                    + "  message = excluded.message";
    private static final String BACKFILL_SEND_COLUMNS_SQL =
            "UPDATE send_history "
                    + "SET send_type = COALESCE(send_type, digest_type), "
                    + "    send_date = COALESCE(send_date, digest_date) "
                    + "WHERE send_type IS NULL OR send_date IS NULL";
    private static final String BACKFILL_DIGEST_COLUMNS_SQL =
            "UPDATE send_history "
                    + "SET digest_type = COALESCE(digest_type, send_type), "
                    + "    digest_date = COALESCE(digest_date, send_date) "
                    + "WHERE digest_type IS NULL OR digest_date IS NULL";

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute(CREATE_SEND_HISTORY_TABLE_SQL);
        ensureLegacyCompatColumns();
        backfillLegacyColumns();
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_send_history_date_type ON send_history(send_date, send_type)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_send_history_date_type ON send_history(send_date, send_type)");
    }

    public boolean hasDailySentToday(LocalDate date) {
        Integer sent = jdbcTemplate.queryForObject(HAS_DAILY_SENT_SQL,
                Integer.class,
                date.toString(),
                DAILY_TYPE,
                LEGACY_DAILY_TYPE
        );
        return sent != null && sent > 0;
    }

    public boolean claimDailyIfAbsent(LocalDate date, String source) {
        int changed = jdbcTemplate.update(CLAIM_DAILY_SQL,
                date.toString(),
                DAILY_TYPE,
                date.toString(),
                DAILY_TYPE,
                Instant.now().toEpochMilli(),
                0,
                "PENDING",
                source
        );
        return changed > 0;
    }

    public void saveDailyResult(LocalDate date, int sentCount, String status, String message) {
        jdbcTemplate.update(SAVE_DAILY_RESULT_SQL,
                date.toString(),
                DAILY_TYPE,
                date.toString(),
                DAILY_TYPE,
                Instant.now().toEpochMilli(),
                Math.max(0, sentCount),
                status,
                message
        );
    }

    private void ensureLegacyCompatColumns() {
        Set<String> columns = new HashSet<>(jdbcTemplate.query(
                "PRAGMA table_info(send_history)",
                (rs, rowNum) -> rs.getString("name")
        ));

        addColumnIfMissing(columns, "send_type", "TEXT");
        addColumnIfMissing(columns, "send_date", "TEXT");
        addColumnIfMissing(columns, "sent_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(columns, "message", "TEXT");
        addColumnIfMissing(columns, "digest_type", "TEXT");
        addColumnIfMissing(columns, "digest_date", "TEXT");
    }

    private void addColumnIfMissing(Set<String> columns, String column, String definition) {
        if (!columns.contains(column)) {
            jdbcTemplate.execute("ALTER TABLE send_history ADD COLUMN " + column + " " + definition);
            columns.add(column);
        }
    }

    private void backfillLegacyColumns() {
        jdbcTemplate.update(BACKFILL_SEND_COLUMNS_SQL);
        jdbcTemplate.update(BACKFILL_DIGEST_COLUMNS_SQL);
    }
}
