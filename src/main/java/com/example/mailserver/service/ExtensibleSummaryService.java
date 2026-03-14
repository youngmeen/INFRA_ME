package com.example.mailserver.service;

import com.example.mailserver.news.NewsCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExtensibleSummaryService implements SummaryService {

    private final RestClient restClient = RestClient.create();
    private final String openAiApiKey;
    private final String openAiModel;

    public ExtensibleSummaryService(
            @Value("${app.openai.api-key:}") String openAiApiKey,
            @Value("${app.openai.model:gpt-4.1-mini}") String openAiModel
    ) {
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
    }

    @Override
    public String summarize(String title, String rawSummary, NewsCategory category) {
        String fallback = heuristicSummary(title, rawSummary, category);
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return fallback;
        }
        try {
            String prompt = """
                    Summarize this news for a software developer in Korean.
                    Keep it concise, 1 sentence, around 55 chars.
                    Include why this matters for practical work.
                    Output only the summary sentence.
                    Category: %s
                    Title: %s
                    Body: %s
                    """.formatted(category.name(), safe(title), safe(rawSummary));

            Map<?, ?> response = restClient.post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .body(Map.of(
                            "model", openAiModel,
                            "temperature", 0.2,
                            "messages", List.of(
                                    Map.of("role", "system", "content", "You are a concise technical news summarizer."),
                                    Map.of("role", "user", "content", prompt)
                            )
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return fallback;
            }
            Object choicesObj = response.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                return fallback;
            }
            Object first = choices.get(0);
            if (!(first instanceof Map<?, ?> firstMap)) {
                return fallback;
            }
            Object messageObj = firstMap.get("message");
            if (!(messageObj instanceof Map<?, ?> messageMap)) {
                return fallback;
            }
            Object contentObj = messageMap.get("content");
            if (!(contentObj instanceof String content) || content.isBlank()) {
                return fallback;
            }
            return content.trim();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String heuristicSummary(String title, String rawSummary, NewsCategory category) {
        String text = safe(rawSummary);
        if (text.isBlank()) {
            text = safe(title);
        }
        if (text.isBlank()) {
            return categoryLabel(category) + " 변화가 실무 우선순위에 영향을 줄 수 있음";
        }

        String normalized = text
                .replaceAll("https?://\\S+", "")
                .replaceAll("<[^>]*>", "")
                .replaceAll("\\[[^\\]]*]", "")
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\s+", " ")
                .replace("...", "")
                .trim();

        if (mostlyAscii(normalized)) {
            return switch (category) {
                case AI -> "AI 모델·도구 변화가 개발 생산성 전략에 영향을 줄 수 있음";
                case DEV -> "개발 도구 업데이트라 팀 워크플로우 변화 여부를 확인할 가치가 있음";
                case INFRA -> "인프라 운영 비용과 안정성에 연결될 수 있어 점검이 필요함";
                case SECURITY -> "보안 리스크 신호라 점검 우선순위를 재정렬할 필요가 있음";
                case BIGTECH -> "플랫폼 정책 변화가 제품 UX와 과금 전략에 영향을 줄 수 있음";
                case KOREA_IT -> "국내 기술 변화가 실무 환경과 협업 방식에 반영될 가능성이 큼";
                case MARKET -> "반도체·실적 이슈가 기술주 흐름에 직접 영향을 줄 수 있음";
                case MACRO -> "금리·환율 변화가 기술주 밸류에이션에 영향을 줄 수 있음";
            };
        }

        if (normalized.contains(".")) {
            normalized = normalized.substring(0, normalized.indexOf('.')).trim();
        }

        if (normalized.length() > 56) {
            normalized = normalized.substring(0, 56).trim();
            int lastSpace = normalized.lastIndexOf(' ');
            if (lastSpace >= 40) {
                normalized = normalized.substring(0, lastSpace).trim();
            }
        }

        if (normalized.length() < 18) {
            normalized = categoryLabel(category) + " 관련 변화로 실무 영향 점검 필요";
        }

        if (!normalized.endsWith("다") && !normalized.endsWith("됨")
                && !normalized.endsWith("함") && !normalized.endsWith("중")
                && !normalized.endsWith("있음")) {
            normalized = normalized + " 확인 필요";
        }
        return normalized;
    }

    private String categoryLabel(NewsCategory category) {
        return switch (category) {
            case AI -> "AI";
            case DEV -> "개발";
            case INFRA -> "인프라";
            case SECURITY -> "보안";
            case BIGTECH -> "빅테크";
            case KOREA_IT -> "국내 IT";
            case MARKET -> "시장";
            case MACRO -> "거시경제";
        };
    }

    private boolean mostlyAscii(String value) {
        if (value == null || value.isBlank()) {
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
