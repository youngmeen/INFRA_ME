package com.example.mailserver.service;

import com.example.mailserver.news.NewsCategory;
import com.example.mailserver.news.NewsDigestView;
import com.example.mailserver.news.StoredNewsItem;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class TelegramFormatService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    public String formatDaily(NewsDigestView view) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>📊 오늘의 Dev+Market 브리핑</b>\n");
        sb.append("기준: ").append(DATE_FORMAT.format(view.generatedAt())).append("\n\n");

        sb.append("<b>오늘 핵심</b>\n");
        for (String line : view.briefing().stream().limit(2).toList()) {
            sb.append("- ").append(escape(shortSentence(line, 64))).append("\n");
        }

        if (!view.topNews().isEmpty()) {
            sb.append("\n━━━━━━━━━━\n");
            sb.append("<b>🔥 TOP NEWS</b>\n");
            sb.append("━━━━━━━━━━\n");

            int index = 1;
            for (StoredNewsItem item : view.topNews().stream().limit(2).toList()) {
                sb.append("\n<b>").append(index++).append(". [").append(displayCategory(item.category())).append("] ")
                        .append(escape(displayTitle(item.title()))).append("</b>\n");
                sb.append("- 핵심: ").append(escape(displaySummary(item.summary(), item.title()))).append("\n");
                sb.append("- 왜 중요?: ").append(escape(whyImportant(item))).append("\n");
            }
        }

        for (Map.Entry<NewsCategory, List<StoredNewsItem>> entry : view.categories().entrySet()) {
            List<StoredNewsItem> items = entry.getValue();
            if (items.isEmpty()) {
                continue;
            }

            sb.append("\n━━━━━━━━━━\n");
            sb.append("<b>").append(displayCategory(entry.getKey())).append("</b>\n");
            sb.append("━━━━━━━━━━\n");

            for (StoredNewsItem item : items) {
                sb.append("- <a href=\"").append(escape(item.link())).append("\"><b>")
                        .append(escape(displayTitle(item.title()))).append("</b></a>\n");
                sb.append("  ").append(escape(displaySummary(item.summary(), item.title()))).append("\n");
                sb.append("  ").append(escape(item.source())).append(" | ")
                        .append("<a href=\"").append(escape(item.link())).append("\">링크</a>\n");
            }
        }

        sb.append("\n총 기사 수: ").append(view.totalCount()).append("건");
        return trimMessage(sb.toString());
    }

    public String formatWeekly(List<StoredNewsItem> top10, Map<NewsCategory, Long> trendCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>🗓 Weekly Dev Report</b>\n");
        sb.append("기준: ").append(DATE_FORMAT.format(java.time.Instant.now())).append(" (KST)\n\n");

        sb.append("<b>TOP 10</b>\n");
        int index = 1;
        for (StoredNewsItem item : top10) {
            sb.append(index++).append(". [").append(displayCategory(item.category())).append("] ")
                    .append("<a href=\"").append(escape(item.link())).append("\">")
                    .append(escape(displayTitle(item.title()))).append("</a>\n");
        }

        sb.append("\n<b>Trend</b>\n");
        for (NewsCategory category : NewsCategory.values()) {
            long count = trendCount.getOrDefault(category, 0L);
            if (count > 0) {
                sb.append("- ").append(displayCategory(category)).append(": ").append(count).append("건\n");
            }
        }
        return trimMessage(sb.toString());
    }

    private String whyImportant(StoredNewsItem item) {
        return switch (item.category()) {
            case AI -> "개발 생산성과 서비스 방향을 바꿀 수 있는 변화임";
            case DEV -> "팀 개발 속도와 워크플로우에 바로 영향을 줄 수 있음";
            case INFRA -> "운영 안정성·비용 최적화 판단에 직접 연결됨";
            case SECURITY -> "보안 점검 우선순위 재정렬에 참고할 가치가 있음";
            case BIGTECH -> "플랫폼 정책이 제품 UX와 비즈니스 결정에 영향을 줄 수 있음";
            case KOREA_IT -> "국내 기술 정책·산업 흐름이 실무 환경에 반영될 수 있음";
            case MARKET -> "반도체·실적·가이던스 이슈가 기술주 흐름에 직접 연결됨";
            case MACRO -> "금리·환율·물가 변화가 기술주 밸류에이션에 영향을 줄 수 있음";
        };
    }

    private String displayCategory(NewsCategory category) {
        return switch (category) {
            case AI -> "🤖 AI";
            case DEV -> "🛠 DEV";
            case INFRA -> "☁ INFRA";
            case SECURITY -> "🛡 SECURITY";
            case BIGTECH -> "🏢 BIGTECH";
            case KOREA_IT -> "🇰🇷 KOREA IT";
            case MARKET -> "📈 시장";
            case MACRO -> "🌍 거시경제";
        };
    }

    private String displayTitle(String rawTitle) {
        String t = safe(rawTitle)
                .replaceAll("\\s+-\\s+[^-]{1,26}$", "")
                .replaceAll("\\s+\\|\\s+[^|]{1,26}$", "")
                .replaceAll("\\s+::\\s+.*$", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (t.length() > 64) {
            t = t.substring(0, 63).trim();
        }
        return t;
    }

    private String displaySummary(String summary, String title) {
        String s = (safe(summary).isBlank() ? safe(title) : safe(summary))
                .replaceAll("https?://\\S+", "")
                .replaceAll("<[^>]*>", "")
                .replaceAll("\\[[^\\]]*]", "")
                .replaceAll("\\([^)]*\\)", "")
                .replace("...", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (s.contains(".")) {
            s = s.substring(0, s.indexOf('.')).trim();
        }
        if (s.length() > 56) {
            s = s.substring(0, 56).trim();
        }
        if (!s.endsWith("다") && !s.endsWith("됨") && !s.endsWith("중")
                && !s.endsWith("함") && !s.endsWith("있음")) {
            s += " 확인 필요";
        }
        return s;
    }

    private String shortSentence(String text, int max) {
        String normalized = safe(text).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, max).trim();
    }

    private String trimMessage(String html) {
        if (html.length() <= 3900) {
            return html;
        }
        return html.substring(0, 3897) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
