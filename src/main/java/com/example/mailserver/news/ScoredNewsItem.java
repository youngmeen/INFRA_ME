package com.example.mailserver.news;

import java.time.Instant;

public record ScoredNewsItem(
        String title,
        String summary,
        NewsCategory category,
        String source,
        String link,
        Instant publishedAt,
        double score,
        String linkHash
) {
}
