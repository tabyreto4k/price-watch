package ru.pricewatch.bot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandRouterTest {

    private static final long CHAT_ID = 42L;

    private final CommandRouter router = new CommandRouter();

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/help", "  /start  "})
    void greetsOnStartAndHelp(String input) {
        assertThat(router.route(CHAT_ID, input).text()).startsWith("Привет");
    }

    @ParameterizedTest
    @ValueSource(strings = {"привет", "/stop", "12345678", "", "   "})
    void refusesEverythingElsePolitely(String input) {
        assertThat(router.route(CHAT_ID, input).text()).startsWith("Не понял");
    }
}
