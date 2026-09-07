package ru.pricewatch.subscription.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import ru.pricewatch.product.model.Product;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** NULL — уведомлять о любом снижении цены. */
    @Column(name = "threshold_percent")
    private Integer thresholdPercent;

    protected Subscription() {}

    public Subscription(long chatId, Product product) {
        this.chatId = chatId;
        this.product = Objects.requireNonNull(product, "product");
    }

    /** Порог хранится в процентах и повторяет ограничение схемы: 1..100. */
    public void setThreshold(int percent) {
        if (percent < 1 || percent > 100) {
            throw new IllegalArgumentException("Порог должен быть от 1 до 100 процентов, а не " + percent);
        }
        this.thresholdPercent = percent;
    }

    public boolean belongsTo(long candidateChatId) {
        return chatId == candidateChatId;
    }

    public Long getId() {
        return id;
    }

    public long getChatId() {
        return chatId;
    }

    public Product getProduct() {
        return product;
    }

    public Integer getThresholdPercent() {
        return thresholdPercent;
    }
}
