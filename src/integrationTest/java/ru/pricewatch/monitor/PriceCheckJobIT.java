package ru.pricewatch.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.math.BigDecimal;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.pricewatch.AbstractPostgresIT;
import ru.pricewatch.bot.BotResponder;
import ru.pricewatch.bot.dto.BotReply;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.product.model.ProductStatus;
import ru.pricewatch.product.repository.PricePointRepository;
import ru.pricewatch.product.repository.ProductRepository;
import ru.pricewatch.product.service.ProductService;
import ru.pricewatch.source.FetchedPrice;
import ru.pricewatch.source.SourceType;
import ru.pricewatch.subscription.repository.SubscriptionRepository;
import ru.pricewatch.subscription.service.SubscriptionService;

class PriceCheckJobIT extends AbstractPostgresIT {

    private static final long CHAT_ID = 42L;
    private static final String GOOD_ARTICLE = "11111111";
    private static final String BROKEN_ARTICLE = "22222222";

    private static final MockWebServer UPSTREAM = new MockWebServer();

    static {
        try {
            UPSTREAM.start();
        } catch (IOException e) {
            throw new IllegalStateException("Не подняли заглушку источника", e);
        }
    }

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) {
        registry.add("pricewatch.source.wildberries.card-url", () -> UPSTREAM.url("/detail?nm=")
                .toString());
        // Ретраи здесь по делу, но ждать между ними в тесте нечего.
        registry.add("pricewatch.source.retry-backoff", () -> "10ms");
    }

    @MockitoBean
    private BotResponder responder;

    @Autowired
    private PriceCheckJob job;

    @Autowired
    private ProductService productService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ProductRepository products;

    @Autowired
    private PricePointRepository pricePoints;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        subscriptions.deleteAll();
        pricePoints.deleteAll();
        products.deleteAll();
        expireLock();
    }

    @Test
    void notifiesSubscriberWhenPriceDropped() {
        Product product = watched(GOOD_ARTICLE, "1990.00");
        respondWith(price(GOOD_ARTICLE, 179000));

        runCheck();

        assertThat(products.findById(product.getId()).orElseThrow().getLastPrice())
                .isEqualByComparingTo("1790.00");
        assertThat(pricePoints.findByProductOrderByRecordedAtAsc(product)).hasSize(2);

        ArgumentCaptor<BotReply> alert = ArgumentCaptor.forClass(BotReply.class);
        verify(responder).send(eq(CHAT_ID), alert.capture());
        assertThat(alert.getValue().text()).contains("⬇");
        // Из уведомления должен открываться график — иначе кнопка в /list остаётся единственным входом.
        assertThat(alert.getValue().keyboard()).isNotEmpty();
    }

    @Test
    void writesNothingWhenPriceIsUnchanged() {
        Product product = watched(GOOD_ARTICLE, "1990.00");
        respondWith(price(GOOD_ARTICLE, 199000));

        runCheck();

        assertThat(pricePoints.findByProductOrderByRecordedAtAsc(product)).hasSize(1);
        verifyNoAlerts();
    }

    @Test
    void staysSilentWhenPriceWentUp() {
        watched(GOOD_ARTICLE, "1990.00");
        respondWith(price(GOOD_ARTICLE, 249000));

        runCheck();

        verifyNoAlerts();
    }

    /** Упавший источник одного товара не должен уносить с собой весь батч. */
    @Test
    void checksSecondProductAfterFirstOneFailed() {
        watched(BROKEN_ARTICLE, "1000.00");
        Product good = watched(GOOD_ARTICLE, "1990.00");
        respondWith(articleAwareDispatcher());

        runCheck();

        assertThat(products.findById(good.getId()).orElseThrow().getLastPrice()).isEqualByComparingTo("1790.00");
    }

    @Test
    void skipsRunWhileTheLockIsHeldBySomeoneElse() {
        watched(GOOD_ARTICLE, "1990.00");
        respondWith(price(GOOD_ARTICLE, 179000));
        jdbcTemplate.update(
                """
                insert into shedlock (name, lock_until, locked_at, locked_by)
                values ('price-check',
                        (now() at time zone 'utc') + interval '10 minutes',
                        now() at time zone 'utc',
                        'другой инстанс')
                on conflict (name) do update
                    set lock_until = excluded.lock_until,
                        locked_at = excluded.locked_at,
                        locked_by = excluded.locked_by
                """);
        int requestsBefore = UPSTREAM.getRequestCount();

        job.checkAll();

        assertThat(UPSTREAM.getRequestCount()).isEqualTo(requestsBefore);
        verifyNoAlerts();
    }

    @Test
    void marksProductUnavailableAfterThreeFailuresAndTellsSubscribersOnce() {
        Product product = watched(BROKEN_ARTICLE, "1000.00");
        respondWith(new MockResponse().setResponseCode(500));

        runCheck();
        runCheck();
        runCheck();
        // Четвёртый прогон товара уже не видит — он выпал из выборки.
        runCheck();

        assertThat(products.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.UNAVAILABLE);
        verify(responder, times(1)).sendText(eq(CHAT_ID), contains("Больше не могу следить"));
    }

    @Test
    void forgetsFailureStreakAfterSourceAnswersAgain() {
        Product product = watched(GOOD_ARTICLE, "1990.00");
        respondWith(new MockResponse().setResponseCode(500));
        runCheck();

        respondWith(price(GOOD_ARTICLE, 199000));
        runCheck();

        assertThat(products.findById(product.getId()).orElseThrow().getFailureStreak())
                .isZero();
    }

    /** Уведомления уходят двумя путями: текстом и ответом с кнопкой. Молчание — это оба. */
    private void verifyNoAlerts() {
        verify(responder, never()).send(anyLong(), any());
        verify(responder, never()).sendText(anyLong(), anyString());
    }

    /**
     * `lockAtLeastFor` держит лок минуту после успешного прогона — защита от частых повторов
     * в проде. Тесту, который гоняет джоб подряд, лок надо освобождать руками.
     */
    private void runCheck() {
        expireLock();
        job.checkAll();
    }

    /**
     * Две грабли разом.
     *
     * <p>UPDATE, а не DELETE: {@code StorageBasedLockProvider} запоминает в памяти JVM, что
     * строка лока уже заведена, и дальше только обновляет её. Удалённая строка означала бы,
     * что UPDATE задевает ноль строк — и лок «занят» до перезапуска.
     *
     * <p>UTC, а не {@code now()}: с {@code usingDbTime()} ShedLock хранит в колонке
     * {@code TIMESTAMP} время по UTC. Локальное время оказалось бы на несколько часов
     * впереди, и лок точно так же не освобождался бы.
     */
    private void expireLock() {
        jdbcTemplate.update(
                """
                update shedlock
                   set lock_until = (now() at time zone 'utc') - interval '1 minute'
                 where name = 'price-check'
                """);
    }

    private Product watched(String article, String price) {
        Product product = productService.registerOrGet(
                SourceType.WILDBERRIES, article, new FetchedPrice("Кружка " + article, new BigDecimal(price)));
        subscriptionService.subscribe(CHAT_ID, product);
        return product;
    }

    private static void respondWith(MockResponse response) {
        UPSTREAM.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return response;
            }
        });
    }

    private static void respondWith(Dispatcher dispatcher) {
        UPSTREAM.setDispatcher(dispatcher);
    }

    private static Dispatcher articleAwareDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = String.valueOf(request.getPath());
                return path.contains("nm=" + GOOD_ARTICLE)
                        ? price(GOOD_ARTICLE, 179000)
                        : new MockResponse().setResponseCode(500);
            }
        };
    }

    private static MockResponse price(String article, long kopecks) {
        String body =
                """
                {"data":{"products":[{"name":"Кружка %s","sizes":[{"price":{"product":%d}}]}]}}"""
                        .formatted(article, kopecks);
        return new MockResponse().setBody(body).setHeader("Content-Type", "application/json");
    }
}
