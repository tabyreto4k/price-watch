package ru.pricewatch.subscription.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.subscription.model.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByChatIdAndProduct(long chatId, Product product);

    List<Subscription> findByProduct(Product product);

    /** join fetch: список читается вне транзакции, а товар нужен вместе с подпиской. */
    @Query("select s from Subscription s join fetch s.product where s.chatId = :chatId order by s.id")
    List<Subscription> findByChatIdWithProduct(long chatId);
}
