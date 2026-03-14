package com.example.mailserver.service;

import com.example.mailserver.config.NewsProperties;
import com.example.mailserver.news.NewsCategory;
import com.example.mailserver.news.NewsItem;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RssFetcherService {

    private static final Logger log = LoggerFactory.getLogger(RssFetcherService.class);

    private final NewsProperties properties;

    public List<NewsItem> fetchSince(Instant since) {
        log.info("[RSS] fetch start since={} timeoutMs={}", since, properties.getRssTimeoutMs());

        List<NewsItem> items = new ArrayList<>();
        int attemptedFeeds = 0;

        for (Map.Entry<NewsCategory, List<String>> entry : sourceMap().entrySet()) {
            NewsCategory category = entry.getKey();
            for (String feedUrl : entry.getValue()) {
                attemptedFeeds++;
                items.addAll(fetchFeed(feedUrl, category, since, false));
            }
        }

        if (properties.isHackerNewsEnabled()) {
            for (String feedUrl : properties.getSources().getHackerNews()) {
                attemptedFeeds++;
                items.addAll(fetchFeed(feedUrl, NewsCategory.DEV, since, true));
            }
        }

        log.info("[RSS] fetch done feeds={} collectedItems={}", attemptedFeeds, items.size());
        return items;
    }

    private Map<NewsCategory, List<String>> sourceMap() {
        Map<NewsCategory, List<String>> map = new EnumMap<>(NewsCategory.class);
        map.put(NewsCategory.AI, properties.getSources().getAi());
        map.put(NewsCategory.DEV, properties.getSources().getDev());
        map.put(NewsCategory.INFRA, properties.getSources().getInfra());
        map.put(NewsCategory.SECURITY, properties.getSources().getSecurity());
        map.put(NewsCategory.BIGTECH, properties.getSources().getBigtech());
        map.put(NewsCategory.KOREA_IT, properties.getSources().getKoreaIt());
        if (properties.isMarketEnabled()) {
            map.put(NewsCategory.MARKET, properties.getSources().getMarket());
        }
        if (properties.isMacroEnabled()) {
            map.put(NewsCategory.MACRO, properties.getSources().getMacro());
        }
        return map;
    }

    private List<NewsItem> fetchFeed(String feedUrl, NewsCategory category, Instant since, boolean hackerNewsMode) {
        List<NewsItem> result = new ArrayList<>();

        try {
            URLConnection connection = new URL(feedUrl).openConnection();
            connection.setConnectTimeout(properties.getRssTimeoutMs());
            connection.setReadTimeout(properties.getRssTimeoutMs());

            try (InputStream in = connection.getInputStream(); XmlReader reader = new XmlReader(in)) {
                SyndFeed feed = new SyndFeedInput().build(reader);
                String sourceName = safe(feed.getTitle()).isBlank() ? feedUrl : safe(feed.getTitle());

                for (SyndEntry entry : feed.getEntries()) {
                    Instant published = resolvePublishedAt(entry);
                    if (published.isBefore(since)) {
                        continue;
                    }

                    String title = safe(entry.getTitle());
                    String link = safe(entry.getLink());
                    if (title.isBlank() || link.isBlank()) {
                        continue;
                    }

                    NewsCategory effectiveCategory = hackerNewsMode ? classifyHackerNews(title, category) : category;
                    result.add(new NewsItem(
                            title,
                            summarize(entry),
                            effectiveCategory,
                            sourceName,
                            link,
                            published
                    ));
                }

                log.info("[RSS] success category={} source={} url={} items={}",
                        category.name(), sourceName, feedUrl, result.size());
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("403") || msg.contains("404")) {
                log.warn("[RSS] skipped status error category={} url={} reason={}", category.name(), feedUrl, msg);
            } else {
                log.warn("[RSS] failed category={} url={} reason={}", category.name(), feedUrl, msg);
            }
        }

        return result;
    }

    private NewsCategory classifyHackerNews(String title, NewsCategory fallback) {
        String lowered = safe(title).toLowerCase(Locale.ROOT);
        if (containsAny(lowered, "security", "cve", "vulnerability", "ransomware", "exploit", "breach")) {
            return NewsCategory.SECURITY;
        }
        if (containsAny(lowered, "kubernetes", "docker", "infra", "cloud", "aws", "gcp", "azure", "devops")) {
            return NewsCategory.INFRA;
        }
        if (containsAny(lowered, "openai", "llm", "ai", "model", "gpt", "anthropic")) {
            return NewsCategory.AI;
        }
        if (containsAny(lowered, "fomc", "cpi", "ppi", "rate", "inflation", "yield", "fx", "dollar")) {
            return NewsCategory.MACRO;
        }
        if (containsAny(lowered, "nvidia", "tsmc", "amd", "earnings", "guidance", "nasdaq", "s&p", "semiconductor")) {
            return NewsCategory.MARKET;
        }
        if (containsAny(lowered, "apple", "google", "meta", "microsoft", "tesla", "amazon")) {
            return NewsCategory.BIGTECH;
        }
        return fallback;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Instant resolvePublishedAt(SyndEntry entry) {
        if (entry.getPublishedDate() != null) {
            return entry.getPublishedDate().toInstant();
        }
        if (entry.getUpdatedDate() != null) {
            return entry.getUpdatedDate().toInstant();
        }
        return Instant.now();
    }

    private String summarize(SyndEntry entry) {
        String raw = "";
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            raw = entry.getDescription().getValue();
        } else if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            raw = String.valueOf(entry.getContents().get(0));
        }
        String text = Jsoup.parse(raw).text().replaceAll("\\s+", " ").trim();
        if (text.isBlank()) {
            return "";
        }
        return text.length() > 280 ? text.substring(0, 280) : text;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
