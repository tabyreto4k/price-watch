package ru.pricewatch.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ru.pricewatch.product.model.PricePoint;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.source.SourceType;

class PriceChartRendererTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

    private static final Product PRODUCT =
            new Product(SourceType.WILDBERRIES, "12345678", "Кружка керамическая", new BigDecimal("1990.00"));

    private final PriceChartRenderer renderer = new PriceChartRenderer();

    @Test
    void rendersHistoryAsPng() {
        byte[] png = renderer.render("Кружка керамическая", history("1990.00", "1890.00", "1790.00"));

        assertThat(png).startsWith(PNG_SIGNATURE).hasSizeGreaterThan(1024);
    }

    /** У товара без единого изменения цены точка одна: рисовать нечего, но падать нельзя. */
    @Test
    void rendersSinglePoint() {
        assertThatCode(() -> renderer.render("Кружка керамическая", history("1990.00")))
                .doesNotThrowAnyException();
    }

    /** Постоянная цена — вырожденный диапазон оси: JFreeChart должен получить его расширенным. */
    @Test
    void rendersFlatHistory() {
        byte[] png = renderer.render("Кружка керамическая", history("1990.00", "1990.00", "1990.00"));

        assertThat(png).startsWith(PNG_SIGNATURE);
    }

    @Test
    void rendersTitleWithoutLosingIt() {
        byte[] withTitle = renderer.render("Очень длинное название товара на две строки", history("1990.00"));
        byte[] withShortTitle = renderer.render("К", history("1990.00"));

        assertThat(withTitle).isNotEqualTo(withShortTitle);
    }

    private static List<PricePoint> history(String... prices) {
        Instant start = Instant.parse("2026-08-01T10:00:00Z");
        return IntStream.range(0, prices.length)
                .mapToObj(i -> new PricePoint(PRODUCT, new BigDecimal(prices[i]), start.plus(Duration.ofDays(i))))
                .toList();
    }
}
