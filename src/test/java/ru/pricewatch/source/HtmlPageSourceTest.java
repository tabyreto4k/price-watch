package ru.pricewatch.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class HtmlPageSourceTest {

    private MockWebServer server;
    private HtmlPageSource source;
    private String pageUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        pageUrl = server.url("/item").toString();
        SourceProperties properties = SourceTestSupport.properties("unused", Map.of("localhost", ".price"));
        source = new HtmlPageSource(
                SourceTestSupport.httpClient(properties), properties, SourceTestSupport.rateLimiters(properties));
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @ParameterizedTest
    @CsvSource({
        "http://localhost/item,true",
        "https://localhost/item,true",
        "https://www.localhost/item,true",
        "https://example.com/item,false",
        "12345678,false",
        "просто текст,false"
    })
    void acceptsOnlyAllowedHosts(String input, boolean supported) {
        assertThat(source.supports(input)).isEqualTo(supported);
    }

    @Test
    void readsPriceFromSelectorAndTitleFromPage() {
        server.enqueue(html("<title>Кружка</title><span class='price'>1 990 ₽</span>"));

        FetchedPrice fetched = source.fetch(pageUrl);

        assertThat(fetched.title()).isEqualTo("Кружка");
        assertThat(fetched.price()).isEqualByComparingTo("1990");
    }

    @Test
    void parsesFractionalPrice() {
        server.enqueue(html("<title>Кружка</title><span class='price'>1 990,50 ₽</span>"));

        assertThat(source.fetch(pageUrl).price()).isEqualByComparingTo("1990.50");
    }

    @Test
    void failsWhenSelectorMatchesNothing() {
        server.enqueue(html("<title>Кружка</title><span class='cost'>1 990 ₽</span>"));

        assertThatThrownBy(() -> source.fetch(pageUrl))
                .isInstanceOf(SourceException.class)
                .hasMessageContaining("ничего не нашёл");
    }

    @Test
    void failsWhenSelectorFoundSomethingUnparseable() {
        server.enqueue(html("<title>Кружка</title><span class='price'>уточняйте</span>"));

        assertThatThrownBy(() -> source.fetch(pageUrl))
                .isInstanceOf(SourceException.class)
                .hasMessageContaining("Не разобрали цену");
    }

    private static MockResponse html(String body) {
        return new MockResponse().setBody("<html>" + body + "</html>").setHeader("Content-Type", "text/html");
    }
}
