package com.example.mailserver.news;

import java.time.Instant;

public record NewsItem(
        String title,
        String summary,
        NewsCategory category,
        String source,
        String link,
        Instant publishedAt
) {
}
