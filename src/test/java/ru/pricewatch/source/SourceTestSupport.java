package ru.pricewatch.source;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import ru.pricewatch.config.WebClientConfig;

/** Общая обвязка тестов источников: реальные конфиги, но с быстрыми ретраями и без лимита. */
final class SourceTestSupport {

    private SourceTestSupport() {}

    static SourceProperties properties(String cardUrl, Map<String, String> selectors) {
        return new SourceProperties(
                "PriceWatchBot/test",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                3,
                // В тестах backoff должен быть незаметным, иначе ретраи растянут прогон на секунды.
                Duration.ofMillis(10),
                new SourceProperties.Wildberries(cardUrl, 1000.0),
                new SourceProperties.HtmlPage(1000.0, selectors));
    }

    static SourceHttpClient httpClient(SourceProperties properties) {
        WebClient webClient = new WebClientConfig().sourceWebClient(WebClient.builder(), properties);
        return new SourceHttpClient(webClient, properties);
    }

    static RateLimiterRegistry rateLimiters(SourceProperties properties) {
        return new ru.pricewatch.config.RateLimiterConfig().rateLimiterRegistry(properties);
    }
}
