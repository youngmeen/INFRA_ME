package com.example.mailserver.controller;

import com.example.mailserver.dto.SendEmailRequest;
import com.example.mailserver.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Map.of("ok", true);
    }

    @PostMapping("/send-email")
    public ResponseEntity<?> sendEmail(@Valid @RequestBody SendEmailRequest request) {
        if (!request.hasBody()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "error", "At least one of text or html is required"
            ));
        }

        try {
            emailService.send(request);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (MessagingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "ok", false,
                    "error", e.getMessage()
            ));
        }
    }
}
