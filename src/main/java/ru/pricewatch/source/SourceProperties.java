package ru.pricewatch.source;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Сетевая дисциплина обращения к чужим сайтам: таймауты, ретраи, лимит запросов
 * и честный User-Agent с контактом.
 */
@ConfigurationProperties("pricewatch.source")
public record SourceProperties(
        String userAgent,
        Duration connectTimeout,
        Duration readTimeout,
        int retryAttempts,
        Duration retryBackoff,
        Wildberries wildberries,
        HtmlPage htmlPage) {

    public record Wildberries(String cardUrl, double requestsPerSecond) {}

    /**
     * Список разрешённых доменов — это и есть ключи {@code selectors}: ходить туда, для чего
     * не задан селектор цены, всё равно бессмысленно.
     */
    public record HtmlPage(double requestsPerSecond, Map<String, String> selectors) {}
}
