package ru.pricewatch.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import ru.pricewatch.exception.SourceException;

/**
 * Карточка товара по артикулу. Эндпоинт неофициальный: смена схемы ответа — ожидаемое
 * событие, а не сбой, поэтому разбор защитный и любое расхождение даёт SourceException.
 */
@Component
public class WildberriesSource implements PriceSource {

    private static final Pattern ARTICLE = Pattern.compile("\\d{6,9}");

    private final SourceHttpClient http;
    private final ObjectMapper objectMapper;
    private final SourceProperties properties;
    private final RateLimiter rateLimiter;

    public WildberriesSource(
            SourceHttpClient http,
            ObjectMapper objectMapper,
            SourceProperties properties,
            RateLimiterRegistry rateLimiters) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.rateLimiter = rateLimiters.rateLimiter(SourceType.WILDBERRIES.name());
    }

    @Override
    public SourceType type() {
        return SourceType.WILDBERRIES;
    }

    @Override
    public boolean supports(String userInput) {
        return ARTICLE.matcher(userInput).matches();
    }

    @Override
    public FetchedPrice fetch(String externalId) {
        String url = properties.wildberries().cardUrl() + externalId;
        String body =
                RateLimiter.decorateSupplier(rateLimiter, () -> http.get(url)).get();
        return parse(body, externalId);
    }

    private FetchedPrice parse(String body, String externalId) {
        JsonNode card;
        try {
            card = objectMapper.readTree(body).path("data").path("products").path(0);
        } catch (Exception e) {
            throw new SourceException("Wildberries вернул не JSON для артикула " + externalId, e);
        }

        String title = card.path("name").asText(null);
        JsonNode price = card.path("sizes").path(0).path("price").path("product");
        if (title == null || title.isBlank() || !price.isNumber()) {
            throw new SourceException("Схема ответа Wildberries изменилась: артикул " + externalId);
        }
        // Цена приходит в копейках.
        return new FetchedPrice(title, BigDecimal.valueOf(price.asLong()).movePointLeft(2));
    }
}
