package com.example.mailserver.news;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NewsDigestView {

    private final Instant generatedAt;
    private final List<String> briefing;
    private final List<StoredNewsItem> topNews;
    private final Map<NewsCategory, List<StoredNewsItem>> categories;
    private final int totalCount;

    public NewsDigestView(Instant generatedAt, List<String> briefing, List<StoredNewsItem> topNews,
                          Map<NewsCategory, List<StoredNewsItem>> categories, int totalCount) {
        this.generatedAt = generatedAt;
        this.briefing = briefing;
        this.topNews = topNews;
        this.categories = categories;
        this.totalCount = totalCount;
    }

    public Instant generatedAt() {
        return generatedAt;
    }
    public Instant getGeneratedAt() { return generatedAt; }

    public List<String> briefing() {
        return briefing;
    }
    public List<String> getBriefing() { return briefing; }

    public List<StoredNewsItem> topNews() {
        return topNews;
    }
    public List<StoredNewsItem> getTopNews() { return topNews; }

    public Map<NewsCategory, List<StoredNewsItem>> categories() {
        return categories;
    }
    public Map<NewsCategory, List<StoredNewsItem>> getCategories() { return categories; }

    public int totalCount() {
        return totalCount;
    }
    public int getTotalCount() { return totalCount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NewsDigestView)) return false;
        NewsDigestView that = (NewsDigestView) o;
        return totalCount == that.totalCount
                && Objects.equals(generatedAt, that.generatedAt)
                && Objects.equals(briefing, that.briefing)
                && Objects.equals(topNews, that.topNews)
                && Objects.equals(categories, that.categories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(generatedAt, briefing, topNews, categories, totalCount);
    }
}
