package com.example.mailserver.service;

import com.example.mailserver.config.NewsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NewsConfigLogService {

    private static final Logger log = LoggerFactory.getLogger(NewsConfigLogService.class);

    private final NewsProperties properties;

    @PostConstruct
    public void logSourceCounts() {
        log.info("[CONFIG] AI RSS count={}", properties.getSources().getAi().size());
        log.info("[CONFIG] DEV RSS count={}", properties.getSources().getDev().size());
        log.info("[CONFIG] INFRA RSS count={}", properties.getSources().getInfra().size());
        log.info("[CONFIG] SECURITY RSS count={}", properties.getSources().getSecurity().size());
        log.info("[CONFIG] BIGTECH RSS count={}", properties.getSources().getBigtech().size());
        log.info("[CONFIG] KOREA_IT RSS count={}", properties.getSources().getKoreaIt().size());
        log.info("[CONFIG] MARKET RSS count={}", properties.getSources().getMarket().size());
        log.info("[CONFIG] MACRO RSS count={}", properties.getSources().getMacro().size());
        log.info("[CONFIG] DIGEST total={} topNews={}", properties.getDigestTotal(), properties.getDigestTopNews());
        log.info("[CONFIG] HACKER_NEWS enabled={} count={} dailyMax={}",
                properties.isHackerNewsEnabled(),
                properties.getSources().getHackerNews().size(),
                properties.getHackerNewsDailyMax());
        log.info("[CONFIG] MARKET enabled={} maxItems={} | MACRO enabled={} maxItems={}",
                properties.isMarketEnabled(), properties.getMaxMarketItems(),
                properties.isMacroEnabled(), properties.getMaxMacroItems());
        log.info("[CONFIG] INTEREST enabled={} keywords={} boost={} topPriority={} maxItems={}",
                properties.getInterest().isEnabled(),
                properties.getInterest().getKeywords().size(),
                properties.getInterest().getKeywordBoostScore(),
                properties.getInterest().isTopNewsInterestPriority(),
                properties.getInterest().getMaxInterestItems());
        log.info("[CONFIG] KOREA_IT lowQualityKeywords={}", properties.getKoreaLowQualityKeywords().size());
    }
}
