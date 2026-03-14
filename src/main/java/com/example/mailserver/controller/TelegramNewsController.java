package com.example.mailserver.controller;

import com.example.mailserver.service.DailyNewsDigestService;
import com.example.mailserver.service.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
public class TelegramNewsController {

    private final DailyNewsDigestService digestService;
    private final WeeklyReportService weeklyReportService;

    @PostMapping("/send-daily-news")
    public ResponseEntity<Map<String, Object>> sendNow() {
        int count = digestService.sendDailyDigest();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "sentCount", count
        ));
    }

    @PostMapping("/send-weekly-news")
    public ResponseEntity<Map<String, Object>> sendWeeklyNow() {
        int count = weeklyReportService.sendWeeklyReport();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "sentCount", count
        ));
    }
}
