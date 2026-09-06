package ru.pricewatch.config;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.pricewatch.source.SourceProperties;
import ru.pricewatch.source.SourceType;

/** Лимит на источник [Р6]: чужой API не должен получать от нас больше, чем мы обещали. */
@Configuration
public class RateLimiterConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(SourceProperties properties) {
        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();
        registry.rateLimiter(
                SourceType.WILDBERRIES.name(),
                oneRequestPer(properties.wildberries().requestsPerSecond(), properties));
        registry.rateLimiter(
                SourceType.HTML_PAGE.name(), oneRequestPer(properties.htmlPage().requestsPerSecond(), properties));
        return registry;
    }

    private static io.github.resilience4j.ratelimiter.RateLimiterConfig oneRequestPer(
            double requestsPerSecond, SourceProperties properties) {
        return io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(
                        Duration.ofNanos((long) (Duration.ofSeconds(1).toNanos() / requestsPerSecond)))
                // Батч проверки готов подождать очереди; отдельный запрос от пользователя — тоже.
                .timeoutDuration(properties.readTimeout().multipliedBy(4))
                .build();
    }
}
