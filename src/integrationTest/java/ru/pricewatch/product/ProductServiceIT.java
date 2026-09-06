package ru.pricewatch.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import ru.pricewatch.AbstractPostgresIT;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.product.repository.PricePointRepository;
import ru.pricewatch.product.repository.ProductRepository;
import ru.pricewatch.source.FetchedPrice;
import ru.pricewatch.source.SourceType;
import ru.pricewatch.subscription.repository.SubscriptionRepository;

class ProductServiceIT extends AbstractPostgresIT {

    private static final String ARTICLE = "12345678";
    private static final FetchedPrice FETCHED = new FetchedPrice("Кружка", new BigDecimal("1990.00"));

    @Autowired
    private ru.pricewatch.product.service.ProductService productService;

    @Autowired
    private ProductRepository products;

    @Autowired
    private PricePointRepository pricePoints;

    @Autowired
    private SubscriptionRepository subscriptions;

    @BeforeEach
    void clean() {
        subscriptions.deleteAll();
        pricePoints.deleteAll();
        products.deleteAll();
    }

    @Test
    void registersProductWithItsFirstPricePoint() {
        Product product = productService.registerOrGet(SourceType.WILDBERRIES, ARTICLE, FETCHED);

        assertThat(product.getId()).isNotNull();
        assertThat(product.getLastPrice()).isEqualByComparingTo("1990.00");
        // Графику нужна хотя бы одна точка сразу после подписки.
        assertThat(pricePoints.findByProductOrderByRecordedAtAsc(product)).hasSize(1);
    }

    @Test
    void returnsTheSameProductForTheSameArticle() {
        Product first = productService.registerOrGet(SourceType.WILDBERRIES, ARTICLE, FETCHED);
        Product second = productService.registerOrGet(
                SourceType.WILDBERRIES, ARTICLE, new FetchedPrice("Кружка", new BigDecimal("1500.00")));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(products.count()).isEqualTo(1);
        // Повторная регистрация — не проверка цены: истории она не трогает.
        assertThat(pricePoints.findByProductOrderByRecordedAtAsc(first)).hasSize(1);
    }

    @Test
    void treatsTheSameArticleFromAnotherSourceAsAnotherProduct() {
        productService.registerOrGet(SourceType.WILDBERRIES, ARTICLE, FETCHED);
        productService.registerOrGet(SourceType.HTML_PAGE, ARTICLE, FETCHED);

        assertThat(products.count()).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateSourceAndExternalId() {
        productService.registerOrGet(SourceType.WILDBERRIES, ARTICLE, FETCHED);

        assertThatThrownBy(() -> products.saveAndFlush(
                        new Product(SourceType.WILDBERRIES, ARTICLE, "Дубль", new BigDecimal("100.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void writesHistoryPointOnlyWhenPriceChanged() {
        Product product = productService.registerOrGet(SourceType.WILDBERRIES, ARTICLE, FETCHED);

        boolean recorded = productService.recordPriceChange(product, new BigDecimal("1990.00"));

        assertThat(recorded).isFalse();
        assertThat(pricePoints.findByProductOrderByRecordedAtAsc(product)).hasSize(1);
    }

    @Test
    void writesHistoryPointAndMovesLastPriceWhenPriceChanged() {
        Product product = productService.registerOrGet(SourceType.WILDBERRIES, ARTICLE, FETCHED);

        boolean recorded = productService.recordPriceChange(product, new BigDecimal("1790.00"));

        assertThat(recorded).isTrue();
        assertThat(products.findById(product.getId()).orElseThrow().getLastPrice())
                .isEqualByComparingTo("1790.00");
        assertThat(pricePoints.findByProductOrderByRecordedAtAsc(product))
                .extracting(point -> point.getPrice().stripTrailingZeros().toPlainString())
                .containsExactly("1990", "1790");
    }

    /** `1990.00` и `1990.0` — одна и та же цена: сравнение только через compareTo. */
    @Test
    void ignoresScaleWhenComparingPrices() {
        Product product = productService.registerOrGet(SourceType.WILDBERRIES, ARTICLE, FETCHED);

        assertThat(productService.recordPriceChange(product, new BigDecimal("1990.0")))
                .isFalse();
    }
}
