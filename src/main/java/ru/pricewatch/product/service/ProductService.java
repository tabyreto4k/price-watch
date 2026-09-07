package ru.pricewatch.product.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pricewatch.product.model.PricePoint;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.product.model.ProductStatus;
import ru.pricewatch.product.repository.PricePointRepository;
import ru.pricewatch.product.repository.ProductRepository;
import ru.pricewatch.source.FetchedPrice;
import ru.pricewatch.source.SourceType;

@Service
public class ProductService {

    private final ProductRepository products;
    private final PricePointRepository pricePoints;

    public ProductService(ProductRepository products, PricePointRepository pricePoints) {
        this.products = products;
        this.pricePoints = pricePoints;
    }

    /** Товар за парой (источник, внешний id) один: второй подписчик получает тот же. */
    @Transactional
    public Product registerOrGet(SourceType source, String externalId, FetchedPrice fetched) {
        return products.findBySourceAndExternalId(source, externalId)
                .orElseGet(() -> register(source, externalId, fetched));
    }

    /**
     * Точка истории пишется ТОЛЬКО при изменении цены [Р9].
     *
     * @return записана ли новая точка
     */
    @Transactional
    public boolean recordPriceChange(Product product, BigDecimal newPrice) {
        if (product.hasPrice(newPrice)) {
            // Источник ответил — серия неудач обнуляется. Если её не было, писать нечего.
            if (product.noteSuccess()) {
                products.save(product);
            }
            return false;
        }
        product.updatePrice(newPrice);
        products.save(product);
        pricePoints.save(new PricePoint(product, newPrice, Instant.now()));
        return true;
    }

    /**
     * Источник не отдал цену. После {@code maxFailures} неудач подряд товар уходит из
     * проверок.
     *
     * @return стал ли товар недоступным именно сейчас — уведомить подписчиков нужно один раз
     */
    @Transactional
    public boolean recordFailure(Product product, int maxFailures) {
        boolean exhausted = product.recordFailure() >= maxFailures && product.getStatus() == ProductStatus.ACTIVE;
        if (exhausted) {
            product.markUnavailable();
        }
        products.save(product);
        return exhausted;
    }

    /**
     * История для графика: точки за окно плюс текущая цена «сейчас». Точка пишется только
     * при изменении [Р9], поэтому без неё линия обрывалась бы на последнем изменении, а у
     * товара, который не менялся дольше окна, точек не осталось бы вовсе.
     */
    @Transactional(readOnly = true)
    public List<PricePoint> chartHistory(Product product, Duration window) {
        Instant now = Instant.now();
        List<PricePoint> history = new ArrayList<>(
                pricePoints.findByProductAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(product, now.minus(window)));
        // Точка «сейчас» никуда не сохраняется: она нужна только линии на графике.
        history.add(new PricePoint(product, product.getLastPrice(), now));
        return history;
    }

    /**
     * @return цена, с которой сравнивается текущая; пусто, пока товар не менялся ни разу
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> previousPrice(Product product) {
        List<PricePoint> lastTwo = pricePoints.findTop2ByProductOrderByRecordedAtDescIdDesc(product);
        return lastTwo.size() < 2
                ? Optional.empty()
                : Optional.of(lastTwo.get(1).getPrice());
    }

    @Transactional(readOnly = true)
    public List<Product> findActiveWithSubscribers() {
        return products.findActiveWithSubscribers();
    }

    private Product register(SourceType source, String externalId, FetchedPrice fetched) {
        Product product = products.save(new Product(source, externalId, fetched.title(), fetched.price()));
        // У товара всегда есть хотя бы одна точка: иначе графику нечего рисовать сразу после подписки.
        pricePoints.save(new PricePoint(product, fetched.price(), Instant.now()));
        return product;
    }
}
