package com.example.mailserver.service;

import com.example.mailserver.news.NewsCategory;

public interface SummaryService {
    String summarize(String title, String rawSummary, NewsCategory category);
}
