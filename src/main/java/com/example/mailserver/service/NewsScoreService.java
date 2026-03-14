package com.example.mailserver.service;

import com.example.mailserver.config.NewsProperties;
import com.example.mailserver.news.NewsCategory;
import com.example.mailserver.news.NewsItem;
import com.example.mailserver.news.ScoredNewsItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NewsScoreService {

    private static final Map<NewsCategory, Double> CATEGORY_BASE = Map.of(
            NewsCategory.AI, 6.4,
            NewsCategory.DEV, 6.2,
            NewsCategory.INFRA, 6.1,
            NewsCategory.SECURITY, 5.6,
            NewsCategory.BIGTECH, 5.3,
            NewsCategory.KOREA_IT, 3.2,
            NewsCategory.MARKET, 6.0,
            NewsCategory.MACRO, 6.1
    );

    private static final Map<String, Double> SOURCE_BONUS = Map.ofEntries(
            Map.entry("openai", 3.6),
            Map.entry("anthropic", 3.2),
            Map.entry("huggingface", 3.0),
            Map.entry("venturebeat", 2.2),
            Map.entry("github", 2.7),
            Map.entry("jetbrains", 2.5),
            Map.entry("martinfowler", 2.5),
            Map.entry("aws", 2.5),
            Map.entry("cloud.google", 2.5),
            Map.entry("azure", 2.3),
            Map.entry("kubernetes", 2.6),
            Map.entry("docker", 2.6),
            Map.entry("infoq", 2.1),
            Map.entry("krebsonsecurity", 2.8),
            Map.entry("darkreading", 2.6),
            Map.entry("etnews", 2.2),
            Map.entry("zdnet", 2.1),
            Map.entry("hackernews", 1.8),
            Map.entry("hacker news", 1.8)
    );

    private final NewsProperties properties;
    private final NewsFilterService filterService;

    public List<ScoredNewsItem> scoreAll(List<NewsItem> items) {
        List<ScoredNewsItem> scored = new ArrayList<>();
        Instant now = Instant.now();

        for (NewsItem item : items) {
            double score = categoryScore(item.category())
                    + sourceScore(item.source(), item.link())
                    + keywordScore(item)
                    + freshnessScore(item.publishedAt(), now)
                    + developerRelevance(item)
                    + marketMacroBoost(item)
                    - lowValuePenalty(item);

            scored.add(new ScoredNewsItem(
                    item.title(),
                    item.summary(),
                    item.category(),
                    item.source(),
                    item.link(),
                    item.publishedAt(),
                    Math.round(score * 100.0) / 100.0,
                    filterService.hash(item.link())
            ));
        }
        return scored;
    }

    private double categoryScore(NewsCategory category) {
        return CATEGORY_BASE.getOrDefault(category, 3.0);
    }

    private double sourceScore(String source, String link) {
        String merged = (safe(source) + " " + safe(link)).toLowerCase(Locale.ROOT);
        double score = 0.0;
        for (Map.Entry<String, Double> bonus : SOURCE_BONUS.entrySet()) {
            if (merged.contains(bonus.getKey())) {
                score += bonus.getValue();
            }
        }
        return score;
    }

    private double keywordScore(NewsItem item) {
        String merged = (safe(item.title()) + " " + safe(item.summary())).toLowerCase(Locale.ROOT);
        double score = 0.0;
        for (String keyword : properties.getImportantKeywords()) {
            if (merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 1.1;
            }
        }
        return score;
    }

    private double freshnessScore(Instant publishedAt, Instant now) {
        long hours = Math.max(0, Duration.between(publishedAt, now).toHours());
        double freshness = 6.2 - (hours * 0.23);
        return Math.max(0.5, freshness);
    }

    private double developerRelevance(NewsItem item) {
        String merged = (safe(item.title()) + " " + safe(item.summary())).toLowerCase(Locale.ROOT);
        double score = 0.0;

        if (containsAny(merged,
                "api", "sdk", "release", "benchmark", "kubernetes", "docker", "spring", "java", "python",
                "agent", "llm", "gpt", "openai", "model", "cve", "vulnerability", "exploit")) {
            score += 1.6;
        }

        if (containsAny(merged, "migration", "deprecate", "breaking", "pricing", "policy", "요금", "정책", "지원 종료")) {
            score += 1.0;
        }

        return score;
    }

    private double marketMacroBoost(NewsItem item) {
        String merged = (safe(item.title()) + " " + safe(item.summary())).toLowerCase(Locale.ROOT);
        double score = 0.0;

        for (String keyword : properties.getMarketKeywords()) {
            if (merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 1.2;
            }
        }

        for (String keyword : properties.getMacroKeywords()) {
            if (merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 1.2;
            }
        }

        if (item.category() == NewsCategory.MARKET
                && containsAny(merged, "실적", "가이던스", "반도체", "나스닥", "코스피", "코스닥", "수출", "관세")) {
            score += 1.7;
        }

        if (item.category() == NewsCategory.MACRO
                && containsAny(merged, "금리", "연준", "fomc", "cpi", "환율", "국채금리", "고용")) {
            score += 1.8;
        }

        return score;
    }

    private double lowValuePenalty(NewsItem item) {
        String merged = (safe(item.title()) + " " + safe(item.summary()) + " " + safe(item.source()))
                .toLowerCase(Locale.ROOT);
        double penalty = 0.0;

        if (containsAny(merged,
                "event", "webinar", "sponsored", "advertisement", "행사", "성료", "모집", "홍보", "보도자료", "협약", "칼럼", "오피니언")) {
            penalty += 2.4;
        }

        if (item.category() == NewsCategory.BIGTECH
                && !containsAny(merged, "api", "ai", "model", "cloud", "developer", "privacy", "security", "policy", "실적")) {
            penalty += 1.2;
        }

        if (item.category() == NewsCategory.SECURITY
                && !containsAny(merged, "cve", "zero-day", "exploit", "ransomware", "취약점", "침해", "patch", "breach")) {
            penalty += 1.1;
        }

        if ((item.category() == NewsCategory.MARKET || item.category() == NewsCategory.MACRO)
                && containsAny(merged, "종목추천", "급등주", "매수", "매도", "재테크", "부동산", "시황 브리핑")) {
            penalty += 2.8;
        }

        return penalty;
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
