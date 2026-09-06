package ru.pricewatch.monitor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Когда снижение цены достойно уведомления [Р9]. Чистая функция: ни БД, ни времени,
 * ни сети — поэтому проверяется таблицей кейсов без Spring.
 */
@Component
public class PriceDropPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param thresholdPercent минимальный процент снижения; {@code null} — любое снижение
     */
    public boolean shouldNotify(BigDecimal oldPrice, BigDecimal newPrice, Integer thresholdPercent) {
        if (newPrice.compareTo(oldPrice) >= 0) {
            return false;
        }
        if (thresholdPercent == null) {
            return true;
        }
        return dropPercent(oldPrice, newPrice).compareTo(BigDecimal.valueOf(thresholdPercent)) >= 0;
    }

    /** На сколько процентов цена упала. Цена всегда больше нуля — делить не на что нечем. */
    public BigDecimal dropPercent(BigDecimal oldPrice, BigDecimal newPrice) {
        return oldPrice.subtract(newPrice).multiply(HUNDRED).divide(oldPrice, 2, RoundingMode.HALF_UP);
    }
}
