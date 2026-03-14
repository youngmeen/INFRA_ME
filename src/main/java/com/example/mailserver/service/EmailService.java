package com.example.mailserver.service;

import com.example.mailserver.dto.SendEmailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public void send(SendEmailRequest request) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(from);
        helper.setTo(request.to());
        if (request.cc() != null && !request.cc().isBlank()) {
            helper.setCc(request.cc());
        }
        if (request.bcc() != null && !request.bcc().isBlank()) {
            helper.setBcc(request.bcc());
        }
        helper.setSubject(request.subject());
        helper.setText(
                request.text() == null ? "" : request.text(),
                request.html() == null ? "" : request.html()
        );

        mailSender.send(message);
    }
}
