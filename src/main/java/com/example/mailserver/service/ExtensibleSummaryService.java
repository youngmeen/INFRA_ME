package com.example.mailserver.service;

import com.example.mailserver.news.NewsCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ExtensibleSummaryService implements SummaryService {

    private final RestTemplate restTemplate = new RestTemplate();
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
            String prompt = buildPrompt(category, title, rawSummary);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey);
            HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(buildRequestBody(prompt), headers);
            Map<?, ?> response = restTemplate.postForObject("https://api.openai.com/v1/chat/completions", request, Map.class);

            if (response == null) {
                return fallback;
            }
            Object choicesObj = response.get("choices");
            if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) {
                return fallback;
            }
            Object first = ((List<?>) choicesObj).get(0);
            if (!(first instanceof Map)) {
                return fallback;
            }
            Object messageObj = ((Map<?, ?>) first).get("message");
            if (!(messageObj instanceof Map)) {
                return fallback;
            }
            Object contentObj = ((Map<?, ?>) messageObj).get("content");
            if (!(contentObj instanceof String)) {
                return fallback;
            }
            String content = ((String) contentObj).trim();
            if (content.isEmpty()) {
                return fallback;
            }
            return content;
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
            if (category == NewsCategory.AI) {
                return "AI 모델·도구 변화가 개발 생산성 전략에 영향을 줄 수 있음";
            }
            if (category == NewsCategory.DEV) {
                return "개발 도구 업데이트라 팀 워크플로우 변화 여부를 확인할 가치가 있음";
            }
            if (category == NewsCategory.INFRA) {
                return "인프라 운영 비용과 안정성에 연결될 수 있어 점검이 필요함";
            }
            if (category == NewsCategory.SECURITY) {
                return "보안 리스크 신호라 점검 우선순위를 재정렬할 필요가 있음";
            }
            if (category == NewsCategory.BIGTECH) {
                return "플랫폼 정책 변화가 제품 UX와 과금 전략에 영향을 줄 수 있음";
            }
            if (category == NewsCategory.KOREA_IT) {
                return "국내 기술 변화가 실무 환경과 협업 방식에 반영될 가능성이 큼";
            }
            if (category == NewsCategory.MARKET) {
                return "반도체·실적 이슈가 기술주 흐름에 직접 영향을 줄 수 있음";
            }
            return "금리·환율 변화가 기술주 밸류에이션에 영향을 줄 수 있음";
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
        if (category == NewsCategory.AI) {
            return "AI";
        }
        if (category == NewsCategory.DEV) {
            return "개발";
        }
        if (category == NewsCategory.INFRA) {
            return "인프라";
        }
        if (category == NewsCategory.SECURITY) {
            return "보안";
        }
        if (category == NewsCategory.BIGTECH) {
            return "빅테크";
        }
        if (category == NewsCategory.KOREA_IT) {
            return "국내 IT";
        }
        if (category == NewsCategory.MARKET) {
            return "시장";
        }
        return "거시경제";
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

    private String buildPrompt(NewsCategory category, String title, String rawSummary) {
        return "Summarize this news for a software developer in Korean.\n"
                + "Keep it concise, 1 sentence, around 55 chars.\n"
                + "Include why this matters for practical work.\n"
                + "Output only the summary sentence.\n"
                + "Category: " + category.name() + "\n"
                + "Title: " + safe(title) + "\n"
                + "Body: " + safe(rawSummary);
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("model", openAiModel);
        root.put("temperature", 0.2);

        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        Map<String, String> system = new LinkedHashMap<String, String>();
        system.put("role", "system");
        system.put("content", "You are a concise technical news summarizer.");
        messages.add(system);

        Map<String, String> user = new LinkedHashMap<String, String>();
        user.put("role", "user");
        user.put("content", prompt);
        messages.add(user);

        root.put("messages", messages);
        return root;
    }
}
