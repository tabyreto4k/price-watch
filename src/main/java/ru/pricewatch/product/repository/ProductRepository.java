package ru.pricewatch.product.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.source.SourceType;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySourceAndExternalId(SourceType source, String externalId);
}
