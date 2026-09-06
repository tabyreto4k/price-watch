package ru.pricewatch.source;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.pricewatch.exception.SourceException;

/**
 * Общая на оба источника сетевая дисциплина: ретраи с экспоненциальным backoff и jitter
 * ТОЛЬКО на 5xx, таймауты и 429 [Р5]. Остальные 4xx — ошибка запроса, повторять её незачем.
 */
@Component
public class SourceHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SourceHttpClient.class);

    private final WebClient webClient;
    private final SourceProperties properties;

    public SourceHttpClient(WebClient sourceWebClient, SourceProperties properties) {
        this.webClient = sourceWebClient;
        this.properties = properties;
    }

    public String get(String uri) {
        try {
            return webClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(retrySpec())
                    .block();
        } catch (RuntimeException e) {
            throw new SourceException("Не смогли получить " + uri, e);
        }
    }

    private Retry retrySpec() {
        return Retry.backoff(properties.retryAttempts(), properties.retryBackoff())
                .jitter(0.5)
                .filter(SourceHttpClient::isRetryable)
                // 429 приходит с Retry-After: ждём не меньше, чем попросили.
                .doBeforeRetryAsync(signal -> retryAfter(signal.failure())
                        .map(delay -> Mono.delay(delay).then())
                        .orElseGet(Mono::empty))
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    private static boolean isRetryable(Throwable failure) {
        if (failure instanceof WebClientResponseException response) {
            return response.getStatusCode().is5xxServerError()
                    || response.getStatusCode().value() == 429;
        }
        return failure instanceof WebClientRequestException || failure instanceof TimeoutException;
    }

    private static Optional<Duration> retryAfter(Throwable failure) {
        if (!(failure instanceof WebClientResponseException response)
                || response.getStatusCode().value() != 429) {
            return Optional.empty();
        }
        String header = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        try {
            return Optional.ofNullable(header).map(value -> Duration.ofSeconds(Long.parseLong(value.trim())));
        } catch (NumberFormatException e) {
            // Retry-After бывает и датой; тогда просто отработает обычный backoff.
            log.debug("Не разобрали Retry-After: {}", header);
            return Optional.empty();
        }
    }
}
