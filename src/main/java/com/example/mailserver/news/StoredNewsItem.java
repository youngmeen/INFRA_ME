package com.example.mailserver.news;

import java.time.Instant;

public record StoredNewsItem(
        long id,
        String title,
        String summary,
        NewsCategory category,
        String source,
        String link,
        Instant publishedAt,
        double score,
        Instant createdAt
) {
}
