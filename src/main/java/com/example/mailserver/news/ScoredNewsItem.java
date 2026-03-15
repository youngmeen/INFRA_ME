package com.example.mailserver.news;

import java.time.Instant;
import java.util.Objects;

public final class ScoredNewsItem {

    private final String title;
    private final String summary;
    private final NewsCategory category;
    private final String source;
    private final String link;
    private final Instant publishedAt;
    private final double score;
    private final String linkHash;

    public ScoredNewsItem(String title, String summary, NewsCategory category, String source,
                          String link, Instant publishedAt, double score, String linkHash) {
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.source = source;
        this.link = link;
        this.publishedAt = publishedAt;
        this.score = score;
        this.linkHash = linkHash;
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

    public double score() {
        return score;
    }
    public double getScore() { return score; }

    public String linkHash() {
        return linkHash;
    }
    public String getLinkHash() { return linkHash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScoredNewsItem)) return false;
        ScoredNewsItem that = (ScoredNewsItem) o;
        return Double.compare(that.score, score) == 0
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && category == that.category
                && Objects.equals(source, that.source)
                && Objects.equals(link, that.link)
                && Objects.equals(publishedAt, that.publishedAt)
                && Objects.equals(linkHash, that.linkHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, summary, category, source, link, publishedAt, score, linkHash);
    }
}
