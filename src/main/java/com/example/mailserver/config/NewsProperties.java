package com.example.mailserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.news")
@Getter
@Setter
public class NewsProperties {

    private String cron = "0 0 9 * * *";
    private String zone = "Asia/Seoul";
    private String weeklyCron = "0 0 9 * * MON";
    private String weeklyZone = "Asia/Seoul";
    private int dailyLimit = 12;
    private int weeklyTop = 10;
    private int perCategoryDaily = 1;
    private int digestTopNews = 2;
    private int digestTotal = 6;
    private int rssTimeoutMs = 8000;
    private boolean hackerNewsEnabled = true;
    private int hackerNewsDailyMax = 2;

    private boolean marketEnabled = true;
    private boolean macroEnabled = true;
    private int maxMarketItems = 2;
    private int maxMacroItems = 2;
    private double englishMarketPenalty = 1.6;
    private double minimumKoreanMarketRatio = 0.7;

    private Dedupe dedupe = new Dedupe();
    private List<String> importantKeywords = new ArrayList<>();
    private List<String> marketKeywords = new ArrayList<>();
    private List<String> macroKeywords = new ArrayList<>();
    private List<String> koreaLowQualityKeywords = new ArrayList<>();
    private List<String> koreanMarketSourceDomains = new ArrayList<>();
    private Interest interest = new Interest();
    private Sources sources = new Sources();

    @Getter
    @Setter
    public static class Dedupe {
        private double titleSimilarityThreshold = 0.88;
    }

    @Getter
    @Setter
    public static class Interest {
        private boolean enabled = true;
        private List<String> keywords = new ArrayList<>();
        private double keywordBoostScore = 1.1;
        private boolean topNewsInterestPriority = true;
        private int maxInterestItems = 2;
    }

    @Getter
    @Setter
    public static class Sources {
        private List<String> ai = new ArrayList<>();
        private List<String> dev = new ArrayList<>();
        private List<String> infra = new ArrayList<>();
        private List<String> security = new ArrayList<>();
        private List<String> bigtech = new ArrayList<>();
        private List<String> koreaIt = new ArrayList<>();
        private List<String> market = new ArrayList<>();
        private List<String> macro = new ArrayList<>();
        private List<String> hackerNews = new ArrayList<>();
    }
}
