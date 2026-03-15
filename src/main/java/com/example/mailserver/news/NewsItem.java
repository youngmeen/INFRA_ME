package com.example.mailserver.news;

import java.time.Instant;
import java.util.Objects;

public final class NewsItem {

    private final String title;
    private final String summary;
    private final NewsCategory category;
    private final String source;
    private final String link;
    private final Instant publishedAt;

    public NewsItem(String title, String summary, NewsCategory category, String source, String link, Instant publishedAt) {
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.source = source;
        this.link = link;
        this.publishedAt = publishedAt;
    }

    public String title() {
        return title;
    }
    public String getTitle() { return title; }

    public String summary() {
        return summary;
    }
    public String getSummary() { return summary; }

    public NewsCategory category() {
        return category;
    }
    public NewsCategory getCategory() { return category; }

    public String source() {
        return source;
    }
    public String getSource() { return source; }

    public String link() {
        return link;
    }
    public String getLink() { return link; }

    public Instant publishedAt() {
        return publishedAt;
    }
    public Instant getPublishedAt() { return publishedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NewsItem)) return false;
        NewsItem newsItem = (NewsItem) o;
        return Objects.equals(title, newsItem.title)
                && Objects.equals(summary, newsItem.summary)
                && category == newsItem.category
                && Objects.equals(source, newsItem.source)
                && Objects.equals(link, newsItem.link)
                && Objects.equals(publishedAt, newsItem.publishedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, summary, category, source, link, publishedAt);
    }
}
