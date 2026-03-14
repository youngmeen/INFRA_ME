package com.example.mailserver.service;

import com.example.mailserver.config.NewsProperties;
import com.example.mailserver.news.NewsCategory;
import com.example.mailserver.news.NewsItem;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NewsFilterService {

    private static final Logger log = LoggerFactory.getLogger(NewsFilterService.class);
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-Z0-9가-힣]+");

    private final NewsProperties properties;

    public List<NewsItem> filterAndDedupe(List<NewsItem> items) {
        List<NewsItem> filtered = new ArrayList<>();
        Set<String> linkHashes = new HashSet<>();

        int keywordRejected = 0;
        int qualityRejected = 0;
        int linkDupRejected = 0;
        int titleDupRejected = 0;

        for (NewsItem item : items) {
            if (!matchesKeywords(item)) {
                keywordRejected++;
                continue;
            }
            if (isLowQuality(item)) {
                qualityRejected++;
                continue;
            }

            String linkHash = hash(item.link());
            if (!linkHashes.add(linkHash)) {
                linkDupRejected++;
                continue;
            }

            if (isNearDuplicate(item, filtered)) {
                titleDupRejected++;
                continue;
            }
            filtered.add(item);
        }

        log.info("[FILTER] input={} kept={} keywordRejected={} qualityRejected={} linkDupRejected={} titleDupRejected={}",
                items.size(), filtered.size(), keywordRejected, qualityRejected, linkDupRejected, titleDupRejected);
        return filtered;
    }

    public String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private boolean matchesKeywords(NewsItem item) {
        List<String> keywords = properties.getImportantKeywords();
        if (keywords.isEmpty()) {
            return true;
        }

        String merged = (safe(item.title()) + " " + safe(item.summary()) + " " + safe(item.source()))
                .toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        if (item.category() == NewsCategory.MARKET || item.category() == NewsCategory.MACRO) {
            for (String keyword : properties.getMarketKeywords()) {
                if (merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            for (String keyword : properties.getMacroKeywords()) {
                if (merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isLowQuality(NewsItem item) {
        String merged = (safe(item.title()) + " " + safe(item.summary()) + " " + safe(item.source()))
                .toLowerCase(Locale.ROOT);

        if (containsAny(merged,
                "행사 개최", "행사 성료", "교육 성료", "수료", "채용", "채용공고", "지원자 모집", "광고", "스폰서드",
                "보도자료", "홍보", "mou", "협약식", "세미나 안내", "웨비나", "newsletter")) {
            return true;
        }

        if (item.category() == NewsCategory.KOREA_IT) {
            if (containsAny(merged,
                    "설명회", "교육", "모집", "기념식", "캠페인", "체험", "개소식", "공모", "시상식")) {
                return true;
            }
            for (String keyword : properties.getKoreaLowQualityKeywords()) {
                if (!keyword.isBlank() && merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }

        if (item.category() == NewsCategory.MARKET || item.category() == NewsCategory.MACRO) {
            if (containsAny(merged,
                    "재테크", "부동산", "칼럼", "오피니언", "종목추천", "급등주", "매수", "매도", "수익률 인증", "회원가입")) {
                return true;
            }
            if (!containsMarketMacroSignal(merged)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsMarketMacroSignal(String text) {
        for (String keyword : properties.getMarketKeywords()) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        for (String keyword : properties.getMacroKeywords()) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearDuplicate(NewsItem target, List<NewsItem> kept) {
        for (NewsItem existing : kept) {
            if (existing.category() != target.category()) {
                continue;
            }
            double similarity = titleSimilarity(existing.title(), target.title());
            if (similarity >= properties.getDedupe().getTitleSimilarityThreshold()) {
                return true;
            }
            if (isVeryCloseTime(existing.publishedAt(), target.publishedAt()) && similarity >= 0.65) {
                return true;
            }
        }
        return false;
    }

    private boolean isVeryCloseTime(Instant a, Instant b) {
        return Math.abs(a.toEpochMilli() - b.toEpochMilli()) < 1000L * 60L * 20L;
    }

    private double titleSimilarity(String a, String b) {
        Set<String> setA = tokenize(a);
        Set<String> setB = tokenize(b);
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0.0;
        }

        int intersection = 0;
        for (String token : setA) {
            if (setB.contains(token)) {
                intersection++;
            }
        }
        int union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private Set<String> tokenize(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        String[] parts = TOKEN_SPLIT.split(normalized);
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
