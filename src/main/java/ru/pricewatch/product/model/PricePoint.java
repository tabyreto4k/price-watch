package ru.pricewatch.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Цена товара на момент времени. Заводится только когда цена изменилась [Р9]. */
@Entity
@Table(name = "price_points")
public class PricePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected PricePoint() {}

    public PricePoint(Product product, BigDecimal price, Instant recordedAt) {
        this.product = Objects.requireNonNull(product, "product");
        this.price = Objects.requireNonNull(price, "price");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
