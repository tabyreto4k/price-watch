package ru.pricewatch.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriceDropPolicyTest {

    private final PriceDropPolicy policy = new PriceDropPolicy();

    @ParameterizedTest(name = "{0} → {1}, порог {2} ⇒ {3}")
    @CsvSource(
            nullValues = "null",
            value = {
                // Рост и равенство не уведомляют никогда: суть продукта — снижение.
                "1990.00, 2100.00, null, false",
                "1990.00, 1990.00, null, false",
                "1990.00, 1990.0,  null, false",
                "1990.00, 2100.00, 10,   false",
                // Без порога годится любое снижение, вплоть до копейки.
                "1990.00, 1789.00, null, true",
                "1990.00, 1989.99, null, true",
                // С порогом сравнивается процент снижения.
                "2000.00, 1900.00, 10,   false",
                "2000.00, 1800.00, 10,   true",
                "2000.00, 1700.00, 10,   true",
                "2000.00, 1999.00, 10,   false",
                "2000.00, 1800.00, 100,  false",
            })
    void decidesWhetherToNotify(BigDecimal oldPrice, BigDecimal newPrice, Integer threshold, boolean expected) {
        assertThat(policy.shouldNotify(oldPrice, newPrice, threshold)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1} = -{2}%")
    @CsvSource({"2000.00, 1800.00, 10.00", "1990.00, 1790.00, 10.05", "100.00, 1.00, 99.00"})
    void computesDropPercent(BigDecimal oldPrice, BigDecimal newPrice, BigDecimal expected) {
        assertThat(policy.dropPercent(oldPrice, newPrice)).isEqualByComparingTo(expected);
    }
}
