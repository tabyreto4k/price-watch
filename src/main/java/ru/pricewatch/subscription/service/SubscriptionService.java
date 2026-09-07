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

    private static final String NO_SUCH_SUBSCRIPTION = "Такой подписки нет — возможно, она уже удалена.";

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

    /**
     * @return удалённая подписка — вызывающему нужно название товара для ответа
     */
    @Transactional
    public Subscription unsubscribe(long chatId, long subscriptionId) {
        Subscription subscription = requireOwned(chatId, subscriptionId);
        subscriptions.delete(subscription);
        return subscription;
    }

    /** Чужой id подписки не должен открывать чужую историю: владельца проверяем на каждом обращении. */
    @Transactional(readOnly = true)
    public Subscription requireOwned(long chatId, long subscriptionId) {
        return subscriptions
                .findByIdWithProduct(subscriptionId)
                .filter(candidate -> candidate.belongsTo(chatId))
                .orElseThrow(() -> new UserInputException(NO_SUCH_SUBSCRIPTION));
    }

    /**
     * «Порог 10» приходит следом за добавлением товара и относится к последней подписке чата:
     * id в команде пользователь не набирает.
     */
    @Transactional
    public Subscription setThreshold(long chatId, int percent) {
        List<Subscription> chatSubscriptions = subscriptions.findByChatIdWithProduct(chatId);
        if (chatSubscriptions.isEmpty()) {
            throw new UserInputException("Сначала пришли артикул или ссылку — порог ставится на подписку.");
        }
        Subscription latest = chatSubscriptions.get(chatSubscriptions.size() - 1);
        latest.setThreshold(percent);
        return subscriptions.save(latest);
    }

    @Transactional(readOnly = true)
    public List<Subscription> listByChat(long chatId) {
        return subscriptions.findByChatIdWithProduct(chatId);
    }
}
