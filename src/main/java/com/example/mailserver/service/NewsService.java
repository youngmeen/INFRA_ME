package com.example.mailserver.service;

import com.example.mailserver.config.NewsProperties;
import com.example.mailserver.news.NewsCategory;
import com.example.mailserver.news.NewsDigestView;
import com.example.mailserver.news.NewsItem;
import com.example.mailserver.news.ScoredNewsItem;
import com.example.mailserver.news.StoredNewsItem;
import com.example.mailserver.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration TODAY_FALLBACK_WINDOW = Duration.ofHours(30);

    private final NewsProperties properties;
    private final RssFetcherService rssFetcherService;
    private final NewsFilterService newsFilterService;
    private final SummaryService summaryService;
    private final NewsScoreService newsScoreService;
    private final NewsRepository newsRepository;

    public int refreshLatestNews() {
        Instant since = Instant.now().minus(Duration.ofHours(30));
        List<NewsItem> fetched = rssFetcherService.fetchSince(since);

        List<NewsItem> filtered = newsFilterService.filterAndDedupe(fetched).stream()
                .map(item -> new NewsItem(
                        item.title(),
                        summaryService.summarize(item.title(), item.summary(), item.category()),
                        item.category(),
                        item.source(),
                        item.link(),
                        item.publishedAt()
                ))
                .collect(Collectors.toList());

        List<ScoredNewsItem> scored = newsScoreService.scoreAll(filtered);
        int changed = newsRepository.upsertAll(scored);

        log.info("[PIPELINE] fetched={} filtered={} scored={} upserted={}",
                fetched.size(), filtered.size(), scored.size(), changed);
        return changed;
    }

    public List<StoredNewsItem> getTodayNews(int limit, NewsCategory category) {
        Instant since = LocalDate.now(KST).atStartOfDay(KST).toInstant();
        List<StoredNewsItem> today = category != null
                ? newsRepository.findSinceByCategory(since, category, limit)
                : newsRepository.findSince(since, limit);
        if (!today.isEmpty()) {
            return today;
        }

        Instant fallbackSince = Instant.now().minus(TODAY_FALLBACK_WINDOW);
        List<StoredNewsItem> fallback = category != null
                ? newsRepository.findSinceByCategory(fallbackSince, category, limit)
                : newsRepository.findSince(fallbackSince, limit);

        log.info("[DIGEST] today window empty. fallbackHours={} category={} fallbackCount={}",
                TODAY_FALLBACK_WINDOW.toHours(), category == null ? "ALL" : category.name(), fallback.size());
        return fallback;
    }

    public List<StoredNewsItem> getWeeklyNews(int limit, NewsCategory category) {
        Instant since = Instant.now().minus(Duration.ofDays(7));
        if (category != null) {
            return newsRepository.findSinceByCategory(since, category, limit);
        }
        return newsRepository.findSince(since, limit);
    }

    public List<StoredNewsItem> searchNews(String query, int limit, NewsCategory category) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return newsRepository.search(query, category, limit);
    }

    public NewsDigestView getTodayDigestView(int limit, NewsCategory category) {
        return buildDigestView(getTodayNews(limit, category), fixedTopCount(), fixedTotalCount(), properties.getPerCategoryDaily());
    }

    public NewsDigestView getWeeklyDigestView(int limit, NewsCategory category) {
        return buildDigestView(getWeeklyNews(limit, category), fixedTopCount(), fixedTotalCount(), properties.getPerCategoryDaily());
    }

    public NewsDigestView searchDigestView(String query, int limit, NewsCategory category) {
        return buildDigestView(searchNews(query, limit, category), fixedTopCount(), fixedTotalCount(), properties.getPerCategoryDaily());
    }

    public NewsDigestView getDailyDigestCandidates() {
        refreshLatestNews();
        return getTodayDigestView(properties.getDailyLimit(), null);
    }

    public List<StoredNewsItem> getWeeklyTopNews() {
        return getWeeklyNews(properties.getWeeklyTop(), null);
    }

    public Map<NewsCategory, Long> getWeeklyTrend() {
        List<StoredNewsItem> weekly = getWeeklyNews(500, null);
        Map<NewsCategory, Long> counts = new EnumMap<>(NewsCategory.class);
        for (NewsCategory category : NewsCategory.values()) {
            counts.put(category, 0L);
        }
        for (StoredNewsItem item : weekly) {
            counts.put(item.category(), counts.getOrDefault(item.category(), 0L) + 1);
        }
        return counts;
    }

    private NewsDigestView buildDigestView(
            List<StoredNewsItem> input,
            int topNewsCount,
            int totalLimit,
            int perCategoryDefault
    ) {
        List<StoredNewsItem> ranked = input.stream()
                .filter(this::passesQualityGate)
                .sorted(Comparator.comparingDouble(this::weightedScore).reversed()
                        .thenComparing(StoredNewsItem::publishedAt, Comparator.reverseOrder()))
                .collect(Collectors.toList());
        if (ranked.isEmpty() && !input.isEmpty()) {
            ranked = input.stream()
                    .sorted(Comparator.comparingDouble(this::weightedScore).reversed()
                            .thenComparing(StoredNewsItem::publishedAt, Comparator.reverseOrder()))
                    .collect(Collectors.toList());
            log.info("[DIGEST] qualityGate fallback applied. input={} fallbackRanked={}", input.size(), ranked.size());
        }

        List<StoredNewsItem> cappedForHn = capHackerNews(ranked, properties.getHackerNewsDailyMax());
        if (cappedForHn.isEmpty()) {
            log.info("[INTEREST] matched=0 selected=0 keywords=-");
            log.info("[DIGEST] interestItems=0 total=0");
            return new NewsDigestView(Instant.now(),
                    List.of("오늘 아침에 바로 볼 만한 개발/투자 뉴스가 아직 없습니다.", "낮 시간에 다시 수집되면 자동 반영됩니다."),
                    List.of(),
                    Map.of(),
                    0);
        }

        int topTarget = Math.max(2, Math.min(topNewsCount, 2));
        int targetTotal = Math.max(5, Math.min(totalLimit, 6));

        List<StoredNewsItem> selectionPool = prioritizeInterest(cappedForHn);
        List<StoredNewsItem> topNews = pickTopNews(selectionPool, topTarget);
        Set<Long> usedIds = new HashSet<>();
        topNews.forEach(item -> usedIds.add(item.id()));

        Map<NewsCategory, List<StoredNewsItem>> categories = new LinkedHashMap<>();
        for (NewsCategory category : NewsCategory.values()) {
            categories.put(category, new ArrayList<>());
        }

        int selectedMarket = (int) topNews.stream().filter(item -> item.category() == NewsCategory.MARKET).count();
        int selectedMacro = (int) topNews.stream().filter(item -> item.category() == NewsCategory.MACRO).count();

        if (properties.isMarketEnabled() && selectedMarket < 1) {
            selectedMarket += pickPriorityBody(selectionPool, usedIds, categories, NewsCategory.MARKET, properties.getMaxMarketItems());
        }
        if (properties.isMacroEnabled() && selectedMacro < 1) {
            selectedMacro += pickPriorityBody(selectionPool, usedIds, categories, NewsCategory.MACRO, properties.getMaxMacroItems());
        }

        for (StoredNewsItem item : selectionPool) {
            if (usedIds.contains(item.id())) {
                continue;
            }
            if (usedIds.size() >= targetTotal) {
                break;
            }

            if ((item.category() == NewsCategory.MARKET && selectedMarket >= properties.getMaxMarketItems())
                    || (item.category() == NewsCategory.MACRO && selectedMacro >= properties.getMaxMacroItems())) {
                continue;
            }

            List<StoredNewsItem> list = categories.get(item.category());
            if (list.size() < Math.max(1, perCategoryDefault)) {
                list.add(item);
                usedIds.add(item.id());
                if (item.category() == NewsCategory.MARKET) {
                    selectedMarket++;
                }
                if (item.category() == NewsCategory.MACRO) {
                    selectedMacro++;
                }
                continue;
            }

            if (list.size() < Math.max(1, perCategoryDefault) + 1 && canAddSecondCard(list.get(0), item)) {
                list.add(item);
                usedIds.add(item.id());
                if (item.category() == NewsCategory.MARKET) {
                    selectedMarket++;
                }
                if (item.category() == NewsCategory.MACRO) {
                    selectedMacro++;
                }
            }
        }

        if (usedIds.size() < targetTotal) {
            for (StoredNewsItem item : selectionPool) {
                if (usedIds.contains(item.id())) {
                    continue;
                }
                if ((item.category() == NewsCategory.MARKET && selectedMarket >= properties.getMaxMarketItems())
                        || (item.category() == NewsCategory.MACRO && selectedMacro >= properties.getMaxMacroItems())) {
                    continue;
                }

                categories.computeIfAbsent(item.category(), key -> new ArrayList<>()).add(item);
                usedIds.add(item.id());
                if (item.category() == NewsCategory.MARKET) {
                    selectedMarket++;
                }
                if (item.category() == NewsCategory.MACRO) {
                    selectedMacro++;
                }
                if (usedIds.size() >= targetTotal) {
                    break;
                }
            }
        }

        Map<NewsCategory, List<StoredNewsItem>> compactCategories = new LinkedHashMap<>();
        for (NewsCategory category : NewsCategory.values()) {
            List<StoredNewsItem> list = categories.getOrDefault(category, List.of());
            if (!list.isEmpty()) {
                compactCategories.put(category, list);
            }
        }

        List<StoredNewsItem> normalizedTop = topNews.stream().map(this::toDigestItem).collect(Collectors.toList());
        Map<NewsCategory, List<StoredNewsItem>> normalizedCategories = new LinkedHashMap<>();
        for (Map.Entry<NewsCategory, List<StoredNewsItem>> entry : compactCategories.entrySet()) {
            normalizedCategories.put(entry.getKey(), entry.getValue().stream().map(this::toDigestItem).collect(Collectors.toList()));
        }

        List<StoredNewsItem> merged = mergedItems(normalizedTop, normalizedCategories);
        int selectedKo = (int) merged.stream().filter(this::isKoreanPreferred).count();
        int selectedEn = merged.size() - selectedKo;
        int availableMarket = (int) cappedForHn.stream().filter(item -> item.category() == NewsCategory.MARKET).count();
        int availableMacro = (int) cappedForHn.stream().filter(item -> item.category() == NewsCategory.MACRO).count();
        int finalMarket = (int) merged.stream().filter(item -> item.category() == NewsCategory.MARKET).count();
        int finalMacro = (int) merged.stream().filter(item -> item.category() == NewsCategory.MACRO).count();
        int matchedInterest = (int) cappedForHn.stream().filter(this::isInterestMatched).count();
        int selectedInterest = (int) merged.stream().filter(this::isInterestMatched).count();
        String selectedKeywords = collectInterestKeywords(merged);

        log.info("[MARKET] selected={} dropped={}", finalMarket, Math.max(0, availableMarket - finalMarket));
        log.info("[MACRO] selected={} dropped={}", finalMacro, Math.max(0, availableMacro - finalMacro));
        log.info("[LANG] selected ko={} en={}", selectedKo, selectedEn);
        log.info("[INTEREST] matched={} selected={} keywords={}", matchedInterest, selectedInterest, selectedKeywords);

        String categorySummary = merged.stream()
                .map(item -> item.category().name())
                .distinct()
                .collect(Collectors.joining(","));

        log.info("[DIGEST] topNews={} total={} categories={}", normalizedTop.size(), usedIds.size(), categorySummary);
        log.info("[DIGEST] interestItems={} total={}", selectedInterest, usedIds.size());

        return new NewsDigestView(
                Instant.now(),
                buildBriefing(normalizedTop, normalizedCategories),
                normalizedTop,
                normalizedCategories,
                usedIds.size()
        );
    }

    private List<StoredNewsItem> mergedItems(
            List<StoredNewsItem> top,
            Map<NewsCategory, List<StoredNewsItem>> categories
    ) {
        List<StoredNewsItem> list = new ArrayList<>(top);
        for (List<StoredNewsItem> items : categories.values()) {
            list.addAll(items);
        }
        return list;
    }

    private int pickPriorityBody(
            List<StoredNewsItem> ranked,
            Set<Long> usedIds,
            Map<NewsCategory, List<StoredNewsItem>> categories,
            NewsCategory category,
            int maxItems
    ) {
        int selected = 0;
        for (StoredNewsItem item : ranked) {
            if (usedIds.contains(item.id()) || item.category() != category) {
                continue;
            }
            List<StoredNewsItem> list = categories.get(category);
            if (list.size() >= maxItems) {
                break;
            }
            list.add(item);
            usedIds.add(item.id());
            selected++;
            break;
        }
        return selected;
    }

    private List<StoredNewsItem> capHackerNews(List<StoredNewsItem> ranked, int max) {
        List<StoredNewsItem> result = new ArrayList<>();
        int hnCount = 0;
        for (StoredNewsItem item : ranked) {
            if (isHackerNews(item)) {
                if (hnCount >= Math.max(0, max)) {
                    continue;
                }
                hnCount++;
            }
            result.add(item);
        }
        return result;
    }

    private boolean isHackerNews(StoredNewsItem item) {
        String source = safe(item.source()).toLowerCase(Locale.ROOT);
        String link = safe(item.link()).toLowerCase(Locale.ROOT);
        return source.contains("hacker news") || link.contains("news.ycombinator.com");
    }

    private List<StoredNewsItem> pickTopNews(List<StoredNewsItem> ranked, int count) {
        List<StoredNewsItem> top = new ArrayList<>();
        Set<NewsCategory> usedCategory = new HashSet<>();
        int interestLimit = Math.max(0, Math.min(count, properties.getInterest().getMaxInterestItems()));

        if (properties.getInterest().isEnabled() && properties.getInterest().isTopNewsInterestPriority() && interestLimit > 0) {
            for (StoredNewsItem item : ranked) {
                if (top.size() >= interestLimit) {
                    break;
                }
                if (!isInterestMatched(item)) {
                    continue;
                }
                top.add(item);
                usedCategory.add(item.category());
            }
        }

        for (StoredNewsItem item : ranked) {
            if (top.size() >= count) {
                break;
            }
            if (top.stream().anyMatch(existing -> existing.id() == item.id())) {
                continue;
            }
            if (!usedCategory.add(item.category()) && top.size() >= Math.max(1, count - 1)) {
                continue;
            }
            top.add(item);
        }

        for (StoredNewsItem item : ranked) {
            if (top.size() >= count) {
                break;
            }
            if (top.stream().anyMatch(existing -> existing.id() == item.id())) {
                continue;
            }
            top.add(item);
        }
        return top;
    }

    private List<String> buildBriefing(List<StoredNewsItem> topNews, Map<NewsCategory, List<StoredNewsItem>> categories) {
        if (topNews.isEmpty()) {
            return List.of("오늘은 빠르게 볼 핵심 이슈가 적습니다.", "점심 전 추가 기사 업데이트를 확인해 주세요.");
        }

        boolean hasMarket = categories.containsKey(NewsCategory.MARKET)
                || categories.containsKey(NewsCategory.MACRO)
                || topNews.stream().anyMatch(item -> item.category() == NewsCategory.MARKET || item.category() == NewsCategory.MACRO);

        String first = hasMarket
                ? "오늘은 AI 인프라 이슈와 반도체/환율 흐름이 핵심입니다."
                : "오늘은 개발/AI 생태계 변화 중심으로 보시면 됩니다.";

        String second = hasMarket
                ? "기술주는 빅테크 실적과 금리 해석 영향을 같이 볼 필요가 있습니다."
                : "정책·플랫폼 변화가 개발 생산성과 제품 방향에 영향을 줄 수 있습니다.";

        return List.of(first, second);
    }

    private boolean canAddSecondCard(StoredNewsItem first, StoredNewsItem candidate) {
        return weightedScore(candidate) >= weightedScore(first) - 0.6;
    }

    private double weightedScore(StoredNewsItem item) {
        double score = item.score();
        String merged = (safe(item.title()) + " " + safe(item.summary()) + " " + safe(item.source())).toLowerCase(Locale.ROOT);

        if (containsAny(merged, "행사", "성료", "모집", "홍보", "보도자료", "sponsored", "advertisement")) {
            score -= 2.5;
        }

        if (item.category() == NewsCategory.SECURITY
                && !containsAny(merged, "cve", "zero-day", "취약점", "exploit", "ransomware", "침해", "breach", "patch")) {
            score -= 1.2;
        }

        if (item.category() == NewsCategory.MARKET || item.category() == NewsCategory.MACRO) {
            if (!isKoreanPreferred(item)) {
                score -= properties.getEnglishMarketPenalty();
            }
            if (containsAny(merged, "시황", "칼럼", "오피니언") && !containsAny(merged, "실적", "가이던스", "금리", "환율", "cpi", "fomc")) {
                score -= 1.8;
            }
        }

        if (containsAny(merged, "openai", "llm", "kubernetes", "docker", "spring", "aws", "azure", "gcp", "java", "api", "sdk")) {
            score += 1.0;
        }

        return score;
    }

    private boolean isKoreanPreferred(StoredNewsItem item) {
        String text = safe(item.title()) + " " + safe(item.summary()) + " " + safe(item.source());
        boolean koText = hasHangul(text);
        String link = safe(item.link()).toLowerCase(Locale.ROOT);

        boolean koDomain = properties.getKoreanMarketSourceDomains().stream()
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .anyMatch(link::contains);

        return koText || koDomain;
    }

    private boolean hasHangul(String text) {
        long hangul = text.chars().filter(ch -> (ch >= 0xAC00 && ch <= 0xD7A3)).count();
        if (text.isBlank()) {
            return false;
        }
        return ((double) hangul / text.length()) >= properties.getMinimumKoreanMarketRatio() * 0.1;
    }

    private boolean passesQualityGate(StoredNewsItem item) {
        String merged = (safe(item.title()) + " " + safe(item.summary()) + " " + safe(item.source())).toLowerCase(Locale.ROOT);

        if (containsAny(merged,
                "행사", "성료", "모집", "채용", "홍보", "광고", "보도자료", "협약식", "기념식", "세미나", "설명회")) {
            return false;
        }

        if (item.category() == NewsCategory.KOREA_IT) {
            if (containsAny(merged, "google 뉴스", "프리미엄콘텐츠", "premium content")) {
                return false;
            }
            if (containsAny(merged,
                    "교육", "캠페인", "체험", "개소", "공모", "수강생", "부트캠프", "연수", "직원 대상")) {
                return false;
            }
            for (String keyword : properties.getKoreaLowQualityKeywords()) {
                if (!keyword.isBlank() && merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }

            if (!containsAny(merged,
                    "ai", "개발", "보안", "클라우드", "데이터", "플랫폼", "반도체", "오픈소스", "llm", "kubernetes", "docker")) {
                return item.score() >= 9.5;
            }
        }

        if (item.category() == NewsCategory.MARKET || item.category() == NewsCategory.MACRO) {
            if (containsAny(merged, "재테크", "부동산", "투자권유", "추천종목", "급등주", "유료회원")) {
                return false;
            }
            boolean signal = false;
            for (String keyword : properties.getMarketKeywords()) {
                if (merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                    signal = true;
                    break;
                }
            }
            if (!signal) {
                for (String keyword : properties.getMacroKeywords()) {
                    if (merged.contains(keyword.toLowerCase(Locale.ROOT))) {
                        signal = true;
                        break;
                    }
                }
            }
            if (!signal) {
                return false;
            }
        }

        return true;
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private List<StoredNewsItem> prioritizeInterest(List<StoredNewsItem> ranked) {
        if (!properties.getInterest().isEnabled()) {
            return ranked;
        }
        List<StoredNewsItem> prioritized = new ArrayList<>();
        for (StoredNewsItem item : ranked) {
            if (isInterestMatched(item)) {
                prioritized.add(item);
            }
        }
        for (StoredNewsItem item : ranked) {
            if (!isInterestMatched(item)) {
                prioritized.add(item);
            }
        }
        return prioritized;
    }

    private boolean isInterestMatched(StoredNewsItem item) {
        if (!properties.getInterest().isEnabled()) {
            return false;
        }
        String merged = (safe(item.title()) + " " + safe(item.summary()) + " " + safe(item.source()))
                .toLowerCase(Locale.ROOT);
        for (String keyword : properties.getInterest().getKeywords()) {
            String normalized = keyword.toLowerCase(Locale.ROOT).trim();
            if (!normalized.isBlank() && merged.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String collectInterestKeywords(List<StoredNewsItem> items) {
        if (!properties.getInterest().isEnabled()) {
            return "-";
        }
        Set<String> matched = new LinkedHashSet<>();
        for (StoredNewsItem item : items) {
            String merged = (safe(item.title()) + " " + safe(item.summary()) + " " + safe(item.source()))
                    .toLowerCase(Locale.ROOT);
            for (String keyword : properties.getInterest().getKeywords()) {
                String normalized = keyword.toLowerCase(Locale.ROOT).trim();
                if (!normalized.isBlank() && merged.contains(normalized)) {
                    matched.add(keyword);
                }
            }
        }
        if (matched.isEmpty()) {
            return "-";
        }
        return String.join(",", matched);
    }

    private int fixedTopCount() {
        return 2;
    }

    private int fixedTotalCount() {
        return Math.max(5, Math.min(properties.getDigestTotal(), 6));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private StoredNewsItem toDigestItem(StoredNewsItem item) {
        return new StoredNewsItem(
                item.id(),
                item.title(),
                normalizeSummary(item),
                item.category(),
                item.source(),
                item.link(),
                item.publishedAt(),
                item.score(),
                item.createdAt()
        );
    }

    private String normalizeSummary(StoredNewsItem item) {
        String text = (safe(item.summary()).isBlank() ? safe(item.title()) : safe(item.summary()))
                .replaceAll("https?://\\S+", "")
                .replaceAll("<[^>]*>", "")
                .replaceAll("\\[[^\\]]*]", "")
                .replaceAll("\\([^)]*\\)", "")
                .replace("...", "")
                .replace("확인 필요", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (mostlyAscii(text)) {
            if (item.category() == NewsCategory.AI) {
                return "AI 모델·도구 변화가 개발 속도와 제품 전략에 직접 영향을 줄 수 있음";
            }
            if (item.category() == NewsCategory.DEV) {
                return "개발 워크플로우와 팀 생산성에 영향을 줄 수 있어 확인할 가치가 있음";
            }
            if (item.category() == NewsCategory.INFRA) {
                return "배포 안정성과 비용 최적화에 영향을 줄 수 있어 점검이 필요함";
            }
            if (item.category() == NewsCategory.SECURITY) {
                return "취약점 대응 우선순위 재정렬에 참고할 실무 신호로 볼 가치가 있음";
            }
            if (item.category() == NewsCategory.BIGTECH) {
                return "플랫폼 정책 변화가 서비스 UX와 과금 전략에 영향을 줄 수 있음";
            }
            if (item.category() == NewsCategory.KOREA_IT) {
                return "국내 기술 변화가 실무 환경과 협업 방식에 반영될 가능성이 큼";
            }
            if (item.category() == NewsCategory.MARKET) {
                return "반도체·빅테크 이슈가 국내외 기술주 흐름에 직접 영향을 줄 수 있음";
            }
            return "금리·환율 변화가 기술주 밸류에이션과 투자 심리에 영향을 줄 수 있음";
        }

        if (text.contains(".")) {
            text = text.substring(0, text.indexOf('.')).trim();
        }
        if (text.length() > 56) {
            text = text.substring(0, 56).trim();
        }
        if (text.isBlank()) {
            text = "실무 영향 가능성이 있어 짧게 확인할 가치가 있음";
        }
        if (!text.endsWith("다") && !text.endsWith("됨") && !text.endsWith("함")
                && !text.endsWith("중") && !text.endsWith("있음")) {
            text += " 확인 필요";
        }
        return text;
    }

    private boolean mostlyAscii(String value) {
        if (value.isBlank()) {
            return false;
        }
        int ascii = 0;
        for (char c : value.toCharArray()) {
            if (c <= 127) {
                ascii++;
            }
        }
        return ((double) ascii / value.length()) >= 0.7;
    }
}
