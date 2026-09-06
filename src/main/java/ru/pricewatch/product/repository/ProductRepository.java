package ru.pricewatch.product.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.source.SourceType;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySourceAndExternalId(SourceType source, String externalId);

    /** Товар без подписчиков проверять незачем, UNAVAILABLE — уже нечем. */
    @Query(
            """
            select p from Product p
            where p.status = ru.pricewatch.product.model.ProductStatus.ACTIVE
              and exists (select 1 from Subscription s where s.product = p)
            order by p.id
            """)
    List<Product> findActiveWithSubscribers();
}
