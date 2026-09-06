package ru.pricewatch.product.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.pricewatch.product.model.PricePoint;
import ru.pricewatch.product.model.Product;

public interface PricePointRepository extends JpaRepository<PricePoint, Long> {

    List<PricePoint> findByProductOrderByRecordedAtAsc(Product product);
}
