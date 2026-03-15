package com.example.mailserver.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class SendHistoryRepository {

    private static final String DAILY_TYPE = "DAILY_NEWS";
    private static final String LEGACY_DAILY_TYPE = "DAILY";
    private static final String HAS_DAILY_SENT_SQL =
            "SELECT COUNT(*) "
                    + "FROM send_history "
                    + "WHERE send_date = ? "
                    + "  AND send_type IN (?, ?) "
                    + "  AND UPPER(status) IN ('SUCCESS', 'SENT')";
    private static final String CLAIM_DAILY_INSERT_SQL =
            "INSERT INTO send_history (send_date, send_type, digest_date, digest_type, sent_at, sent_count, status, message) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String CLAIM_DAILY_UPDATE_SQL =
            "UPDATE send_history "
                    + "SET sent_at = ?, digest_date = ?, digest_type = ?, status = 'PENDING', message = ? "
                    + "WHERE send_date = ? "
                    + "  AND send_type = ? "
                    + "  AND UPPER(status) NOT IN ('SUCCESS', 'SENT', 'PENDING')";
    private static final String SAVE_DAILY_RESULT_SQL =
            "INSERT INTO send_history (send_date, send_type, digest_date, digest_type, sent_at, sent_count, status, message) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "  digest_date = VALUES(digest_date), "
                    + "  digest_type = VALUES(digest_type), "
                    + "  sent_at = VALUES(sent_at), "
                    + "  sent_count = VALUES(sent_count), "
                    + "  status = VALUES(status), "
                    + "  message = VALUES(message)";

    private final JdbcTemplate jdbcTemplate;
    private final SendStateRedisRepository sendStateRedisRepository;

    public boolean hasDailySentToday(LocalDate date) {
        if (sendStateRedisRepository.hasDailySent(date)) {
            return true;
        }

        Integer sent = jdbcTemplate.queryForObject(HAS_DAILY_SENT_SQL,
                Integer.class,
                date,
                DAILY_TYPE,
                LEGACY_DAILY_TYPE
        );
        boolean result = sent != null && sent > 0;
        if (result) {
            sendStateRedisRepository.markDailySent(date, "SUCCESS");
        }
        return result;
    }

    public boolean claimDailyIfAbsent(LocalDate date, String source) {
        long now = Instant.now().toEpochMilli();
        try {
            int inserted = jdbcTemplate.update(CLAIM_DAILY_INSERT_SQL,
                    date,
                    DAILY_TYPE,
                    date,
                    DAILY_TYPE,
                    now,
                    0,
                    "PENDING",
                    source
            );
            return inserted > 0;
        } catch (DuplicateKeyException ex) {
            int updated = jdbcTemplate.update(CLAIM_DAILY_UPDATE_SQL,
                    now,
                    date,
                    DAILY_TYPE,
                    source,
                    date,
                    DAILY_TYPE
            );
            return updated > 0;
        }
    }

    public void saveDailyResult(LocalDate date, int sentCount, String status, String message) {
        jdbcTemplate.update(SAVE_DAILY_RESULT_SQL,
                date,
                DAILY_TYPE,
                date,
                DAILY_TYPE,
                Instant.now().toEpochMilli(),
                Math.max(0, sentCount),
                status,
                message
        );
        if ("SUCCESS".equalsIgnoreCase(status) || "SENT".equalsIgnoreCase(status)) {
            sendStateRedisRepository.markDailySent(date, status);
        }
    }
}
