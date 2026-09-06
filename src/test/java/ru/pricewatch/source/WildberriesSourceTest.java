package ru.pricewatch.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.pricewatch.exception.SourceException;

class WildberriesSourceTest {

    private static final String ARTICLE = "12345678";
    private static final String CARD_JSON =
            """
            {"data":{"products":[
              {"id":12345678,"name":"Кружка керамическая",
               "sizes":[{"price":{"basic":199000,"product":179000}}]}
            ]}}""";

    private MockWebServer server;
    private WildberriesSource source;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        SourceProperties properties =
                SourceTestSupport.properties(server.url("/detail?nm=").toString(), Map.of());
        source = new WildberriesSource(
                SourceTestSupport.httpClient(properties),
                new ObjectMapper(),
                properties,
                SourceTestSupport.rateLimiters(properties));
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @ParameterizedTest
    @CsvSource({"12345678,true", "123456,true", "123456789,true", "12345,false", "1234567890,false", "abc,false"})
    void recognisesArticleNumbers(String input, boolean supported) {
        assertThat(source.supports(input)).isEqualTo(supported);
    }

    @Test
    void readsTitleAndPriceFromCard() {
        server.enqueue(json(CARD_JSON));

        FetchedPrice fetched = source.fetch(ARTICLE);

        assertThat(fetched.title()).isEqualTo("Кружка керамическая");
        // Цена приходит в копейках.
        assertThat(fetched.price()).isEqualByComparingTo("1790.00");
    }

    @Test
    void retriesServerErrorsAndSucceedsOnThirdAttempt() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(json(CARD_JSON));

        assertThat(source.fetch(ARTICLE).price()).isEqualByComparingTo("1790.00");
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    /** 404 — ошибка запроса, а не сбой: повторять её значит долбить чужой сервер зря. */
    @Test
    void doesNotRetryClientErrors() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> source.fetch(ARTICLE)).isInstanceOf(SourceException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void failsWhenResponseSchemaChanged() {
        server.enqueue(json("{\"data\":{\"products\":[{\"name\":\"Кружка\"}]}}"));

        assertThatThrownBy(() -> source.fetch(ARTICLE))
                .isInstanceOf(SourceException.class)
                .hasMessageContaining("Схема ответа");
    }

    @Test
    void failsWhenResponseIsNotJson() {
        server.enqueue(new MockResponse().setBody("<html>что-то пошло не так</html>"));

        assertThatThrownBy(() -> source.fetch(ARTICLE)).isInstanceOf(SourceException.class);
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).setHeader("Content-Type", "application/json");
    }
}
