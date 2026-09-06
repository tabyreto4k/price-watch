package ru.pricewatch.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import ru.pricewatch.source.SourceType;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SourceType source;

    @Column(name = "external_id", nullable = false, length = 512)
    private String externalId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "last_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal lastPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductStatus status;

    @Column(name = "failure_streak", nullable = false)
    private int failureStreak;

    protected Product() {}

    public Product(SourceType source, String externalId, String title, BigDecimal lastPrice) {
        this.source = Objects.requireNonNull(source, "source");
        this.externalId = requireText(externalId, "externalId");
        this.title = requireText(title, "title");
        this.lastPrice = requirePositive(lastPrice);
        this.status = ProductStatus.ACTIVE;
    }

    /** Новая цена и, если товар считался пропавшим, возврат в проверки: источник снова отвечает. */
    public void updatePrice(BigDecimal newPrice) {
        this.lastPrice = requirePositive(newPrice);
        this.status = ProductStatus.ACTIVE;
        this.failureStreak = 0;
    }

    /**
     * Источник ответил, хотя цена и не изменилась.
     *
     * @return была ли серия неудач сброшена — если нет, писать в БД нечего
     */
    public boolean noteSuccess() {
        if (failureStreak == 0) {
            return false;
        }
        failureStreak = 0;
        return true;
    }

    /**
     * @return сколько раз подряд источник уже не отдал цену
     */
    public int recordFailure() {
        return ++failureStreak;
    }

    public void markUnavailable() {
        this.status = ProductStatus.UNAVAILABLE;
    }

    public boolean hasPrice(BigDecimal price) {
        return lastPrice.compareTo(price) == 0;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " не может быть пустым");
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Цена должна быть больше нуля, а не " + price);
        }
        return price;
    }

    public Long getId() {
        return id;
    }

    public SourceType getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public int getFailureStreak() {
        return failureStreak;
    }
}
