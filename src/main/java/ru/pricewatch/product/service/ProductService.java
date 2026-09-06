package ru.pricewatch.product.service;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pricewatch.product.model.PricePoint;
import ru.pricewatch.product.model.Product;
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
            return false;
        }
        product.updatePrice(newPrice);
        products.save(product);
        pricePoints.save(new PricePoint(product, newPrice, Instant.now()));
        return true;
    }

    private Product register(SourceType source, String externalId, FetchedPrice fetched) {
        Product product = products.save(new Product(source, externalId, fetched.title(), fetched.price()));
        // У товара всегда есть хотя бы одна точка: иначе графику нечего рисовать сразу после подписки.
        pricePoints.save(new PricePoint(product, fetched.price(), Instant.now()));
        return product;
    }
}
