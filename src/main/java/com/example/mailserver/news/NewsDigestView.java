package com.example.mailserver.news;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NewsDigestView(
        Instant generatedAt,
        List<String> briefing,
        List<StoredNewsItem> topNews,
        Map<NewsCategory, List<StoredNewsItem>> categories,
        int totalCount
) {
}
