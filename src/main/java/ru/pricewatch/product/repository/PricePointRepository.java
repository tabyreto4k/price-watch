package ru.pricewatch.product.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.pricewatch.product.model.PricePoint;
import ru.pricewatch.product.model.Product;

public interface PricePointRepository extends JpaRepository<PricePoint, Long> {

    List<PricePoint> findByProductOrderByRecordedAtAsc(Product product);

    List<PricePoint> findByProductAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(Product product, Instant since);

    /** Две последние точки: вторая — цена, с которой сравнивается текущая. */
    List<PricePoint> findTop2ByProductOrderByRecordedAtDescIdDesc(Product product);
}
