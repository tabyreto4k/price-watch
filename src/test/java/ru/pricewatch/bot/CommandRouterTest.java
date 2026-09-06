package ru.pricewatch.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.pricewatch.exception.SourceException;
import ru.pricewatch.exception.UserInputException;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.product.service.ProductService;
import ru.pricewatch.source.FetchedPrice;
import ru.pricewatch.source.ResolvedInput;
import ru.pricewatch.source.SourceRouter;
import ru.pricewatch.source.SourceType;
import ru.pricewatch.subscription.service.SubscriptionService;

class CommandRouterTest {

    private static final long CHAT_ID = 42L;
    private static final String ARTICLE = "12345678";
    private static final ResolvedInput RESOLVED = new ResolvedInput(SourceType.WILDBERRIES, ARTICLE);

    private final SourceRouter sourceRouter = mock(SourceRouter.class);
    private final ProductService productService = mock(ProductService.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final CommandRouter router = new CommandRouter(sourceRouter, productService, subscriptionService);

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/help", "  /start  "})
    void greetsOnStartAndHelp(String input) {
        assertThat(router.route(CHAT_ID, input).text()).startsWith("Привет");

        verify(sourceRouter, never()).resolve(any());
    }

    @Test
    void subscribesToProductAndAnswersWithItsPrice() {
        FetchedPrice fetched = new FetchedPrice("Кружка керамическая", new BigDecimal("1790.00"));
        Product product = new Product(SourceType.WILDBERRIES, ARTICLE, fetched.title(), fetched.price());
        when(sourceRouter.resolve(ARTICLE)).thenReturn(RESOLVED);
        when(sourceRouter.fetch(RESOLVED)).thenReturn(fetched);
        when(productService.registerOrGet(SourceType.WILDBERRIES, ARTICLE, fetched))
                .thenReturn(product);

        String reply = router.route(CHAT_ID, ARTICLE).text();

        verify(subscriptionService).subscribe(CHAT_ID, product);
        assertThat(reply).isEqualTo("Слежу: Кружка керамическая, сейчас 1 790 ₽");
    }

    @Test
    void trimsInputBeforeRouting() {
        when(sourceRouter.resolve(ARTICLE)).thenReturn(RESOLVED);
        when(sourceRouter.fetch(RESOLVED)).thenReturn(new FetchedPrice("Кружка", BigDecimal.TEN));
        when(productService.registerOrGet(any(), any(), any()))
                .thenReturn(new Product(SourceType.WILDBERRIES, ARTICLE, "Кружка", BigDecimal.TEN));

        router.route(CHAT_ID, "  " + ARTICLE + "  ");

        verify(sourceRouter).resolve(ARTICLE);
    }

    /** «Не понял» теперь живёт в SourceRouter — на два одинаковых текста делить его незачем. */
    @Test
    void letsUnknownInputBubbleUpAsUserInputException() {
        when(sourceRouter.resolve("привет")).thenThrow(new UserInputException("Не понял."));

        assertThatThrownBy(() -> router.route(CHAT_ID, "привет")).isInstanceOf(UserInputException.class);

        verify(subscriptionService, never()).subscribe(eq(CHAT_ID), any());
    }

    @Test
    void doesNotSubscribeWhenSourceFailed() {
        when(sourceRouter.resolve(ARTICLE)).thenReturn(RESOLVED);
        when(sourceRouter.fetch(RESOLVED)).thenThrow(new SourceException("Wildberries молчит"));

        assertThatThrownBy(() -> router.route(CHAT_ID, ARTICLE)).isInstanceOf(SourceException.class);

        verify(productService, never()).registerOrGet(any(), any(), any());
        verify(subscriptionService, never()).subscribe(eq(CHAT_ID), any());
    }
}
