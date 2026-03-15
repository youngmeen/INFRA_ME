package com.example.mailserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class TelegramService {

    private final RestTemplate restTemplate;

    @Value("${app.telegram.bot-token}")
    private String botToken;

    @Value("${app.telegram.chat-id}")
    private String chatId;

    public TelegramService() {
        this.restTemplate = new RestTemplate();
    }

    public void sendMessage(String text) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        body.add("text", truncate(text, 4000));
        body.add("disable_web_page_preview", "true");

        restTemplate.postForEntity(url, body, String.class);
    }

    public void sendHtmlMessage(String html) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        body.add("text", truncate(html, 4000));
        body.add("parse_mode", "HTML");
        body.add("disable_web_page_preview", "true");

        restTemplate.postForEntity(url, body, String.class);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
