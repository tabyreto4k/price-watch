package ru.pricewatch.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.pricewatch.exception.UserInputException;

class SourceRouterTest {

    private final SourceRouter router = new SourceRouter(List.of(
            fake(SourceType.WILDBERRIES, input -> input.matches("\\d{6,9}")),
            fake(SourceType.HTML_PAGE, input -> input.startsWith("https://shop.example/"))));

    @Test
    void routesArticleNumberToWildberries() {
        assertThat(router.resolve("12345678")).isEqualTo(new ResolvedInput(SourceType.WILDBERRIES, "12345678"));
    }

    @Test
    void routesAllowedUrlToHtmlPage() {
        assertThat(router.resolve("https://shop.example/item"))
                .isEqualTo(new ResolvedInput(SourceType.HTML_PAGE, "https://shop.example/item"));
    }

    @Test
    void trimsInputBeforeRouting() {
        assertThat(router.resolve("  12345678  ").externalId()).isEqualTo("12345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"привет", "https://other.example/item", "12345", "", "   "})
    void refusesInputNoSourceUnderstands(String input) {
        assertThatThrownBy(() -> router.resolve(input)).isInstanceOf(UserInputException.class);
    }

    @Test
    void fetchesThroughTheSourceThatOwnsTheType() {
        FetchedPrice fetched = router.fetch(new ResolvedInput(SourceType.HTML_PAGE, "https://shop.example/item"));

        assertThat(fetched.title()).isEqualTo("HTML_PAGE");
    }

    private static PriceSource fake(SourceType type, Predicate<String> supports) {
        return new PriceSource() {
            @Override
            public SourceType type() {
                return type;
            }

            @Override
            public boolean supports(String userInput) {
                return supports.test(userInput);
            }

            @Override
            public FetchedPrice fetch(String externalId) {
                return new FetchedPrice(type.name(), BigDecimal.ONE);
            }
        };
    }
}
