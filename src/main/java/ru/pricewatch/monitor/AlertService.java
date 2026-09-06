package ru.pricewatch.monitor;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pricewatch.bot.BotResponder;
import ru.pricewatch.bot.PriceFormatter;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.subscription.model.Subscription;
import ru.pricewatch.subscription.repository.SubscriptionRepository;

/** Рассылка подписчикам товара. Порог у каждого свой, поэтому решение — на подписку. */
@Service
public class AlertService {

    private final SubscriptionRepository subscriptions;
    private final PriceDropPolicy policy;
    private final PriceFormatter priceFormatter;
    private final BotResponder responder;

    public AlertService(
            SubscriptionRepository subscriptions,
            PriceDropPolicy policy,
            PriceFormatter priceFormatter,
            BotResponder responder) {
        this.subscriptions = subscriptions;
        this.policy = policy;
        this.priceFormatter = priceFormatter;
        this.responder = responder;
    }

    @Transactional(readOnly = true)
    public void notifyDrop(Product product, BigDecimal oldPrice, BigDecimal newPrice) {
        String text = dropText(product, oldPrice, newPrice);
        for (Subscription subscription : subscriptions.findByProduct(product)) {
            if (policy.shouldNotify(oldPrice, newPrice, subscription.getThresholdPercent())) {
                responder.sendText(subscription.getChatId(), text);
            }
        }
    }

    /** Отправляется один раз — товар уже ушёл из выборки проверок. */
    @Transactional(readOnly = true)
    public void notifyUnavailable(Product product) {
        String text = "Больше не могу следить за «%s»: источник перестал отдавать цену.".formatted(product.getTitle());
        for (Subscription subscription : subscriptions.findByProduct(product)) {
            responder.sendText(subscription.getChatId(), text);
        }
    }

    private String dropText(Product product, BigDecimal oldPrice, BigDecimal newPrice) {
        return "⬇ %s: %s → %s ₽ (−%s%%)"
                .formatted(
                        product.getTitle(),
                        priceFormatter.format(oldPrice),
                        priceFormatter.format(newPrice),
                        policy.dropPercent(oldPrice, newPrice)
                                .stripTrailingZeros()
                                .toPlainString());
    }
}
