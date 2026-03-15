package com.example.mailserver.repository;

import com.example.mailserver.news.NewsCategory;
import com.example.mailserver.news.ScoredNewsItem;
import com.example.mailserver.news.StoredNewsItem;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NewsRepository {

    private static final String CREATE_NEWS_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS news ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "title TEXT NOT NULL,"
                    + "summary TEXT NOT NULL,"
                    + "category TEXT NOT NULL,"
                    + "source TEXT NOT NULL,"
                    + "link TEXT NOT NULL,"
                    + "published_at INTEGER NOT NULL,"
                    + "score REAL NOT NULL,"
                    + "created_at INTEGER NOT NULL,"
                    + "link_hash TEXT NOT NULL UNIQUE"
                    + ")";
    private static final String UPSERT_NEWS_SQL =
            "INSERT INTO news (title, summary, category, source, link, published_at, score, created_at, link_hash) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(link_hash) DO UPDATE SET "
                    + "title = excluded.title, "
                    + "summary = excluded.summary, "
                    + "category = excluded.category, "
                    + "source = excluded.source, "
                    + "link = excluded.link, "
                    + "published_at = excluded.published_at, "
                    + "score = CASE WHEN excluded.score > news.score THEN excluded.score ELSE news.score END";
    private static final String FIND_SINCE_SQL =
            "SELECT id, title, summary, category, source, link, published_at, score, created_at "
                    + "FROM news "
                    + "WHERE published_at >= ? "
                    + "ORDER BY score DESC, published_at DESC "
                    + "LIMIT ?";
    private static final String FIND_SINCE_BY_CATEGORY_SQL =
            "SELECT id, title, summary, category, source, link, published_at, score, created_at "
                    + "FROM news "
                    + "WHERE published_at >= ? AND category = ? "
                    + "ORDER BY score DESC, published_at DESC "
                    + "LIMIT ?";
    private static final String SEARCH_BASE_SQL =
            "SELECT id, title, summary, category, source, link, published_at, score, created_at "
                    + "FROM news "
                    + "WHERE (LOWER(title) LIKE ? OR LOWER(summary) LIKE ? OR LOWER(source) LIKE ?)";

    private static final RowMapper<StoredNewsItem> ROW_MAPPER = new RowMapper<>() {
        @Override
        public StoredNewsItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StoredNewsItem(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    NewsCategory.valueOf(rs.getString("category")),
                    rs.getString("source"),
                    rs.getString("link"),
                    Instant.ofEpochMilli(rs.getLong("published_at")),
                    rs.getDouble("score"),
                    Instant.ofEpochMilli(rs.getLong("created_at"))
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute(CREATE_NEWS_TABLE_SQL);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_news_published_at ON news(published_at DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_news_category ON news(category)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_news_score ON news(score DESC)");
    }

    public int upsertAll(List<ScoredNewsItem> items) {
        if (items.isEmpty()) {
            return 0;
        }

        int[][] results = jdbcTemplate.batchUpdate(UPSERT_NEWS_SQL,
                items,
                items.size(),
                (ps, item) -> {
                    ps.setString(1, item.title());
                    ps.setString(2, item.summary());
                    ps.setString(3, item.category().name());
                    ps.setString(4, item.source());
                    ps.setString(5, item.link());
                    ps.setLong(6, item.publishedAt().toEpochMilli());
                    ps.setDouble(7, item.score());
                    ps.setLong(8, Instant.now().toEpochMilli());
                    ps.setString(9, item.linkHash());
                });

        int changed = 0;
        for (int[] batch : results) {
            for (int result : batch) {
                if (result > 0) {
                    changed++;
                }
            }
        }
        return changed;
    }

    public List<StoredNewsItem> findSince(Instant since, int limit) {
        return jdbcTemplate.query(FIND_SINCE_SQL,
                ROW_MAPPER,
                since.toEpochMilli(),
                limit
        );
    }

    public List<StoredNewsItem> findSinceByCategory(Instant since, NewsCategory category, int limit) {
        return jdbcTemplate.query(FIND_SINCE_BY_CATEGORY_SQL,
                ROW_MAPPER,
                since.toEpochMilli(),
                category.name(),
                limit
        );
    }

    public List<StoredNewsItem> search(String query, NewsCategory category, int limit) {
        StringBuilder sql = new StringBuilder(SEARCH_BASE_SQL);
        if (category != null) {
            sql.append(" AND category = ? ");
        }
        sql.append(" ORDER BY score DESC, published_at DESC LIMIT ? ");

        String q = "%" + query.toLowerCase() + "%";
        if (category == null) {
            return jdbcTemplate.query(sql.toString(), ROW_MAPPER, q, q, q, limit);
        }
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, q, q, q, category.name(), limit);
    }
}
