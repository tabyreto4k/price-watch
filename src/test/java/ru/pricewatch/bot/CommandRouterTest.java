package ru.pricewatch.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.pricewatch.bot.dto.BotButton;
import ru.pricewatch.bot.dto.BotReply;
import ru.pricewatch.chart.PriceChartRenderer;
import ru.pricewatch.exception.SourceException;
import ru.pricewatch.exception.UserInputException;
import ru.pricewatch.product.model.PricePoint;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.product.service.ProductService;
import ru.pricewatch.source.FetchedPrice;
import ru.pricewatch.source.ResolvedInput;
import ru.pricewatch.source.SourceRouter;
import ru.pricewatch.source.SourceType;
import ru.pricewatch.subscription.model.Subscription;
import ru.pricewatch.subscription.service.SubscriptionService;

class CommandRouterTest {

    private static final long CHAT_ID = 42L;
    private static final long SUBSCRIPTION_ID = 12L;
    private static final String ARTICLE = "12345678";
    private static final ResolvedInput RESOLVED = new ResolvedInput(SourceType.WILDBERRIES, ARTICLE);
    private static final byte[] PNG = {1, 2, 3};

    private final SourceRouter sourceRouter = mock(SourceRouter.class);
    private final ProductService productService = mock(ProductService.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final PriceChartRenderer chartRenderer = mock(PriceChartRenderer.class);
    private final CommandRouter router = new CommandRouter(
            sourceRouter,
            productService,
            subscriptionService,
            chartRenderer,
            new PriceFormatter(),
            Duration.ofDays(90));

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/help", "  /start  "})
    void greetsOnStartAndHelp(String input) {
        assertThat(router.route(CHAT_ID, input).text()).startsWith("Привет");

        verify(sourceRouter, never()).resolve(any());
    }

    @Test
    void subscribesToProductAndAnswersWithItsPrice() {
        FetchedPrice fetched = new FetchedPrice("Кружка керамическая", new BigDecimal("1790.00"));
        Product product = product("Кружка керамическая", "1790.00");
        when(sourceRouter.resolve(ARTICLE)).thenReturn(RESOLVED);
        when(sourceRouter.fetch(RESOLVED)).thenReturn(fetched);
        when(productService.registerOrGet(SourceType.WILDBERRIES, ARTICLE, fetched))
                .thenReturn(product);

        String reply = router.route(CHAT_ID, ARTICLE).text();

        verify(subscriptionService).subscribe(CHAT_ID, product);
        assertThat(reply).startsWith("Слежу: Кружка керамическая, сейчас 1 790 ₽");
        // Про порог сказано сразу: команду «порог N» иначе неоткуда узнать.
        assertThat(reply).contains("порог 10");
    }

    @Test
    void trimsInputBeforeRouting() {
        when(sourceRouter.resolve(ARTICLE)).thenReturn(RESOLVED);
        when(sourceRouter.fetch(RESOLVED)).thenReturn(new FetchedPrice("Кружка", BigDecimal.TEN));
        when(productService.registerOrGet(any(), any(), any())).thenReturn(product("Кружка", "10"));

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

    @Test
    void listWithoutSubscriptionsTellsHowToAddOne() {
        when(subscriptionService.listByChat(CHAT_ID)).thenReturn(List.of());

        BotReply reply = router.route(CHAT_ID, "/list");

        assertThat(reply.text()).contains("Пока ни за чем не слежу");
        assertThat(reply.keyboard()).isEmpty();
    }

    @Test
    void listShowsPriceDynamicsAndButtonsForEverySubscription() {
        Product product = product("Кружка керамическая", "1790.00");
        when(subscriptionService.listByChat(CHAT_ID)).thenReturn(List.of(subscription(SUBSCRIPTION_ID, product, 10)));
        when(productService.previousPrice(product)).thenReturn(Optional.of(new BigDecimal("1990.00")));

        BotReply reply = router.route(CHAT_ID, "/list");

        assertThat(reply.text())
                .contains("#12 Кружка керамическая")
                .contains("1 790 ₽ ↓ было 1 990 ₽")
                .contains("порог 10%");
        assertThat(reply.keyboard())
                .singleElement()
                .extracting(row -> row.stream().map(BotButton::callbackData).toList())
                .isEqualTo(List.of("chart:12", "drop:12"));
    }

    /** У товара без единого изменения цены сравнивать не с чем — стрелки в строке нет. */
    @Test
    void listOmitsDynamicsUntilThePriceMoves() {
        Product product = product("Кружка керамическая", "1790.00");
        when(subscriptionService.listByChat(CHAT_ID)).thenReturn(List.of(subscription(SUBSCRIPTION_ID, product, null)));
        when(productService.previousPrice(product)).thenReturn(Optional.empty());

        BotReply reply = router.route(CHAT_ID, "/list");

        assertThat(reply.text()).contains("1 790 ₽").doesNotContain("было").doesNotContain("порог");
    }

    /** Товар, за которым больше не следят, обязан быть виден в списке: цена в нём застыла. */
    @Test
    void listMarksProductsTheSourceStoppedAnswering() {
        Product product = product("Кружка керамическая", "1790.00");
        product.markUnavailable();
        when(subscriptionService.listByChat(CHAT_ID)).thenReturn(List.of(subscription(SUBSCRIPTION_ID, product, null)));
        when(productService.previousPrice(product)).thenReturn(Optional.empty());

        assertThat(router.route(CHAT_ID, "/list").text()).contains("источник перестал отдавать цену");
    }

    @Test
    void chartButtonAnswersWithRenderedPhotoAndItsNumbers() {
        Product product = product("Кружка керамическая", "1790.00");
        when(subscriptionService.requireOwned(CHAT_ID, SUBSCRIPTION_ID))
                .thenReturn(subscription(SUBSCRIPTION_ID, product, null));
        List<PricePoint> history = history(product, "1990.00", "1790.00");
        when(productService.chartHistory(eq(product), any())).thenReturn(history);
        when(chartRenderer.render("Кружка керамическая", history)).thenReturn(PNG);

        BotReply reply = router.routeCallback(CHAT_ID, "chart:12");

        assertThat(reply.photo()).isEqualTo(PNG);
        assertThat(reply.text())
                .contains("сейчас 1 790 ₽")
                .contains("мин 1 790 ₽")
                .contains("макс 1 990 ₽");
    }

    @Test
    void chartCommandGoesTheSameWayAsTheButton() {
        Product product = product("Кружка керамическая", "1790.00");
        when(subscriptionService.requireOwned(CHAT_ID, SUBSCRIPTION_ID))
                .thenReturn(subscription(SUBSCRIPTION_ID, product, null));
        when(productService.chartHistory(eq(product), any())).thenReturn(history(product, "1790.00"));
        when(chartRenderer.render(any(), any())).thenReturn(PNG);

        assertThat(router.route(CHAT_ID, "/chart_12").photo()).isEqualTo(PNG);
    }

    @Test
    void deleteButtonUnsubscribesAndNamesTheProduct() {
        Product product = product("Кружка керамическая", "1790.00");
        when(subscriptionService.unsubscribe(CHAT_ID, SUBSCRIPTION_ID))
                .thenReturn(subscription(SUBSCRIPTION_ID, product, null));

        BotReply reply = router.routeCallback(CHAT_ID, "drop:12");

        assertThat(reply.text()).contains("Больше не слежу за «Кружка керамическая»");
    }

    @ParameterizedTest
    @ValueSource(strings = {"мусор", "chart:", "chart:абв", "unknown:12"})
    void rejectsCallbackItCannotRead(String data) {
        assertThatThrownBy(() -> router.routeCallback(CHAT_ID, data)).isInstanceOf(UserInputException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"порог 10", "Порог  10"})
    void thresholdCommandStoresPercent(String input) {
        Product product = product("Кружка керамическая", "1790.00");
        when(subscriptionService.setThreshold(CHAT_ID, 10)).thenReturn(subscription(SUBSCRIPTION_ID, product, 10));

        assertThat(router.route(CHAT_ID, input).text()).contains("Порог 10%").contains("Кружка керамическая");
    }

    /** Цифр в команде сколько угодно, в `int` и `long` — нет: переполнение должно быть ответом, а не стеком. */
    @ParameterizedTest
    @ValueSource(strings = {"порог 99999999999", "/chart_99999999999999999999"})
    void answersInsteadOfOverflowingOnAbsurdNumbers(String input) {
        assertThatThrownBy(() -> router.route(CHAT_ID, input)).isInstanceOf(UserInputException.class);

        verify(subscriptionService, never()).setThreshold(anyLong(), anyInt());
        verify(subscriptionService, never()).requireOwned(anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"порог 0", "порог 101"})
    void rejectsThresholdOutsideOfItsRange(String input) {
        assertThatThrownBy(() -> router.route(CHAT_ID, input)).isInstanceOf(UserInputException.class);

        verify(subscriptionService, never()).setThreshold(anyLong(), anyInt());
    }

    private static Product product(String title, String price) {
        return new Product(SourceType.WILDBERRIES, ARTICLE, title, new BigDecimal(price));
    }

    private static Subscription subscription(long id, Product product, Integer threshold) {
        Subscription subscription = new Subscription(CHAT_ID, product);
        if (threshold != null) {
            subscription.setThreshold(threshold);
        }
        // Id проставляет БД, а транспорту он нужен для кнопок: в юнит-тесте выставляем руками.
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }

    private static List<PricePoint> history(Product product, String... prices) {
        Instant start = Instant.parse("2026-08-01T10:00:00Z");
        return IntStream.range(0, prices.length)
                .mapToObj(i -> new PricePoint(product, new BigDecimal(prices[i]), start.plus(Duration.ofDays(i))))
                .toList();
    }
}
