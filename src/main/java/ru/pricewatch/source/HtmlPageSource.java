package ru.pricewatch.source;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import ru.pricewatch.exception.SourceException;

/** Произвольная страница товара: CSS-селектор цены задаётся на домен в конфиге. */
@Component
public class HtmlPageSource implements PriceSource {

    private final SourceHttpClient http;
    private final SourceProperties properties;
    private final RateLimiter rateLimiter;

    public HtmlPageSource(SourceHttpClient http, SourceProperties properties, RateLimiterRegistry rateLimiters) {
        this.http = http;
        this.properties = properties;
        this.rateLimiter = rateLimiters.rateLimiter(SourceType.HTML_PAGE.name());
    }

    @Override
    public SourceType type() {
        return SourceType.HTML_PAGE;
    }

    @Override
    public boolean supports(String userInput) {
        return selectorFor(userInput).isPresent();
    }

    @Override
    public FetchedPrice fetch(String externalId) {
        String selector = selectorFor(externalId)
                .orElseThrow(() -> new SourceException("Для этого домена не задан селектор цены: " + externalId));

        String html = RateLimiter.decorateSupplier(rateLimiter, () -> http.get(externalId))
                .get();
        var document = Jsoup.parse(html, externalId);

        Element priceElement = document.selectFirst(selector);
        if (priceElement == null) {
            throw new SourceException("Селектор '" + selector + "' ничего не нашёл на " + externalId);
        }
        String title = Optional.ofNullable(document.title())
                .filter(value -> !value.isBlank())
                .orElse(externalId);
        return new FetchedPrice(title, parsePrice(priceElement.text(), externalId));
    }

    /** Allow-list доменов — это ключи selectors: без селектора ходить на сайт незачем. */
    private Optional<String> selectorFor(String userInput) {
        return host(userInput).map(properties.htmlPage().selectors()::get).filter(selector -> !selector.isBlank());
    }

    private static Optional<String> host(String userInput) {
        try {
            URI uri = new URI(userInput.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return Optional.empty();
            }
            return Optional.ofNullable(uri.getHost()).map(value -> value.replaceFirst("^www\\.", ""));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /** «1 990 ₽», «1 990,50 ₽» — режем всё, кроме цифр и разделителя дробной части. */
    private static BigDecimal parsePrice(String raw, String url) {
        String digits = raw.replaceAll("[^0-9,.]", "").replace(',', '.');
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            throw new SourceException("Не разобрали цену '" + raw + "' на " + url, e);
        }
    }
}
