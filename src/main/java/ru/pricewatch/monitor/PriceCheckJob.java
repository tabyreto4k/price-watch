package ru.pricewatch.monitor;

import java.math.BigDecimal;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.pricewatch.exception.SourceException;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.product.service.ProductService;
import ru.pricewatch.source.FetchedPrice;
import ru.pricewatch.source.ResolvedInput;
import ru.pricewatch.source.SourceRouter;

/** Периодический обход товаров, за которыми кто-то следит. */
@Component
public class PriceCheckJob {

    private static final Logger log = LoggerFactory.getLogger(PriceCheckJob.class);

    private final ProductService productService;
    private final SourceRouter sourceRouter;
    private final AlertService alertService;
    private final int maxSourceFailures;

    public PriceCheckJob(
            ProductService productService,
            SourceRouter sourceRouter,
            AlertService alertService,
            @Value("${pricewatch.monitor.max-source-failures}") int maxSourceFailures) {
        this.productService = productService;
        this.sourceRouter = sourceRouter;
        this.alertService = alertService;
        this.maxSourceFailures = maxSourceFailures;
    }

    @Scheduled(
            fixedDelayString = "${pricewatch.monitor.check-interval}",
            initialDelayString = "${pricewatch.monitor.initial-delay}")
    @SchedulerLock(name = "price-check", lockAtMostFor = "PT25M", lockAtLeastFor = "PT1M")
    public void checkAll() {
        List<Product> batch = productService.findActiveWithSubscribers();
        log.info("Проверяем цены: {} товаров", batch.size());
        for (Product product : batch) {
            try {
                check(product);
            } catch (RuntimeException e) {
                // Один товар не должен уносить с собой весь батч.
                log.error("Проверка товара {} упала, продолжаем", product.getId(), e);
            }
        }
    }

    private void check(Product product) {
        BigDecimal oldPrice = product.getLastPrice();
        FetchedPrice fetched;
        try {
            fetched = sourceRouter.fetch(new ResolvedInput(product.getSource(), product.getExternalId()));
        } catch (SourceException e) {
            log.warn("Источник не отдал цену для товара {}", product.getId(), e);
            if (productService.recordFailure(product, maxSourceFailures)) {
                alertService.notifyUnavailable(product);
            }
            return;
        }

        if (productService.recordPriceChange(product, fetched.price())) {
            alertService.notifyDrop(product, oldPrice, fetched.price());
        }
    }
}
