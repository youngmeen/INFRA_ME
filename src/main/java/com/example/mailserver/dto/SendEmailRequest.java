package com.example.mailserver.dto;

import jakarta.validation.constraints.NotBlank;

public record SendEmailRequest(
        @NotBlank String to,
        String cc,
        String bcc,
        @NotBlank String subject,
        String text,
        String html
) {
    public boolean hasBody() {
        return (text != null && !text.isBlank()) || (html != null && !html.isBlank());
    }
}
