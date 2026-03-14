package com.example.mailserver.controller;

import com.example.mailserver.news.NewsCategory;
import com.example.mailserver.news.NewsDigestView;
import com.example.mailserver.news.StoredNewsItem;
import com.example.mailserver.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping("/today")
    public List<StoredNewsItem> today(
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) NewsCategory category
    ) {
        return newsService.getTodayNews(limit, category);
    }

    @GetMapping("/today/digest")
    public NewsDigestView todayDigest(
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) NewsCategory category
    ) {
        return newsService.getTodayDigestView(limit, category);
    }

    @GetMapping("/weekly")
    public List<StoredNewsItem> weekly(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) NewsCategory category
    ) {
        return newsService.getWeeklyNews(limit, category);
    }

    @GetMapping("/weekly/digest")
    public NewsDigestView weeklyDigest(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) NewsCategory category
    ) {
        return newsService.getWeeklyDigestView(limit, category);
    }

    @GetMapping("/search")
    public List<StoredNewsItem> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) NewsCategory category
    ) {
        return newsService.searchNews(q, limit, category);
    }

    @GetMapping("/search/digest")
    public NewsDigestView searchDigest(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) NewsCategory category
    ) {
        return newsService.searchDigestView(q, limit, category);
    }
}
