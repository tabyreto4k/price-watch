package ru.pricewatch.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import ru.pricewatch.AbstractPostgresIT;
import ru.pricewatch.exception.UserInputException;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.product.repository.PricePointRepository;
import ru.pricewatch.product.repository.ProductRepository;
import ru.pricewatch.product.service.ProductService;
import ru.pricewatch.source.FetchedPrice;
import ru.pricewatch.source.SourceType;
import ru.pricewatch.subscription.model.Subscription;
import ru.pricewatch.subscription.repository.SubscriptionRepository;
import ru.pricewatch.subscription.service.SubscriptionService;

class SubscriptionFlowIT extends AbstractPostgresIT {

    private static final long CHAT_ID = 42L;
    private static final long OTHER_CHAT_ID = 99L;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ProductService productService;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private ProductRepository products;

    @Autowired
    private PricePointRepository pricePoints;

    private Product product;

    @BeforeEach
    void setUp() {
        subscriptions.deleteAll();
        pricePoints.deleteAll();
        products.deleteAll();
        product = productService.registerOrGet(
                SourceType.WILDBERRIES, "12345678", new FetchedPrice("Кружка", new BigDecimal("1990.00")));
    }

    @Test
    void subscribesChatToProduct() {
        Subscription subscription = subscriptionService.subscribe(CHAT_ID, product);

        assertThat(subscription.getId()).isNotNull();
        assertThat(subscription.getThresholdPercent()).isNull();
    }

    @Test
    void repeatedSubscriptionReturnsTheExistingOne() {
        Subscription first = subscriptionService.subscribe(CHAT_ID, product);
        Subscription second = subscriptionService.subscribe(CHAT_ID, product);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(subscriptions.count()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateChatAndProductAtTheDatabaseLevel() {
        subscriptionService.subscribe(CHAT_ID, product);

        assertThatThrownBy(() -> subscriptions.saveAndFlush(new Subscription(CHAT_ID, product)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsSubscriptionsOfTheChatWithTheirProducts() {
        subscriptionService.subscribe(CHAT_ID, product);
        subscriptionService.subscribe(OTHER_CHAT_ID, product);

        assertThat(subscriptionService.listByChat(CHAT_ID)).singleElement().satisfies(subscription -> assertThat(
                        subscription.getProduct().getTitle())
                .isEqualTo("Кружка"));
    }

    @Test
    void unsubscribesOwnSubscription() {
        Subscription subscription = subscriptionService.subscribe(CHAT_ID, product);

        subscriptionService.unsubscribe(CHAT_ID, subscription.getId());

        assertThat(subscriptions.count()).isZero();
    }

    @Test
    void setsThresholdOnTheSubscriptionAddedLast() {
        subscriptionService.subscribe(CHAT_ID, product);
        Product another = productService.registerOrGet(
                SourceType.WILDBERRIES, "87654321", new FetchedPrice("Чайник", new BigDecimal("2490.00")));
        Subscription latest = subscriptionService.subscribe(CHAT_ID, another);

        Subscription updated = subscriptionService.setThreshold(CHAT_ID, 10);

        assertThat(updated.getId()).isEqualTo(latest.getId());
        assertThat(subscriptions.findById(latest.getId()).orElseThrow().getThresholdPercent())
                .isEqualTo(10);
    }

    @Test
    void refusesThresholdWhenTheChatHasNoSubscriptions() {
        assertThatThrownBy(() -> subscriptionService.setThreshold(CHAT_ID, 10)).isInstanceOf(UserInputException.class);
    }

    /** Инвариант сущности повторяет CHECK схемы: за границу 1..100 порог не пускается. */
    @Test
    void rejectsThresholdOutsideOfItsRange() {
        subscriptionService.subscribe(CHAT_ID, product);

        assertThatThrownBy(() -> subscriptionService.setThreshold(CHAT_ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadsOwnSubscriptionTogetherWithItsProduct() {
        Subscription subscription = subscriptionService.subscribe(CHAT_ID, product);

        Subscription found = subscriptionService.requireOwned(CHAT_ID, subscription.getId());

        assertThat(found.getProduct().getTitle()).isEqualTo("Кружка");
    }

    @Test
    void refusesToOpenSomeoneElsesSubscription() {
        Subscription foreign = subscriptionService.subscribe(OTHER_CHAT_ID, product);

        assertThatThrownBy(() -> subscriptionService.requireOwned(CHAT_ID, foreign.getId()))
                .isInstanceOf(UserInputException.class);
    }

    /** Чужой id подписки не должен удалять чужую подписку. */
    @Test
    void refusesToUnsubscribeSomeoneElsesSubscription() {
        Subscription foreign = subscriptionService.subscribe(OTHER_CHAT_ID, product);

        assertThatThrownBy(() -> subscriptionService.unsubscribe(CHAT_ID, foreign.getId()))
                .isInstanceOf(UserInputException.class);
        assertThat(subscriptions.count()).isEqualTo(1);
    }
}
