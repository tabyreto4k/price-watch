package ru.pricewatch.subscription.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pricewatch.exception.UserInputException;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.subscription.model.Subscription;
import ru.pricewatch.subscription.repository.SubscriptionRepository;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;

    public SubscriptionService(SubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** Повторная подписка на тот же товар возвращает существующую, а не падает на уникальном индексе. */
    @Transactional
    public Subscription subscribe(long chatId, Product product) {
        return subscriptions
                .findByChatIdAndProduct(chatId, product)
                .orElseGet(() -> subscriptions.save(new Subscription(chatId, product)));
    }

    @Transactional
    public void unsubscribe(long chatId, long subscriptionId) {
        Subscription subscription = subscriptions
                .findById(subscriptionId)
                .filter(candidate -> candidate.belongsTo(chatId))
                .orElseThrow(() -> new UserInputException("Такой подписки нет — возможно, она уже удалена."));
        subscriptions.delete(subscription);
    }

    @Transactional(readOnly = true)
    public List<Subscription> listByChat(long chatId) {
        return subscriptions.findByChatIdWithProduct(chatId);
    }
}
