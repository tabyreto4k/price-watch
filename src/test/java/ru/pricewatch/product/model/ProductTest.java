package ru.pricewatch.product.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.pricewatch.source.SourceType;

class ProductTest {

    private static Product product() {
        return new Product(SourceType.WILDBERRIES, "12345678", "Кружка", new BigDecimal("1990.00"));
    }

    @Test
    void isActiveRightAfterCreation() {
        assertThat(product().getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void refusesNonPositivePrice(String price) {
        BigDecimal value = price == null ? null : new BigDecimal(price);

        assertThatThrownBy(() -> new Product(SourceType.WILDBERRIES, "12345678", "Кружка", value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void refusesBlankTitle(String title) {
        assertThatThrownBy(() -> new Product(SourceType.WILDBERRIES, "12345678", title, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesPriceByValueNotByScale() {
        assertThat(product().hasPrice(new BigDecimal("1990.0"))).isTrue();
        assertThat(product().hasPrice(new BigDecimal("1790.00"))).isFalse();
    }

    /** Источник снова ответил — товар возвращается в проверки сам. */
    @Test
    void newPriceRevivesUnavailableProduct() {
        Product product = product();
        product.markUnavailable();

        product.updatePrice(new BigDecimal("1790.00"));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.getLastPrice()).isEqualByComparingTo("1790.00");
    }
}
