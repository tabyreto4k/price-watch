package ru.pricewatch.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.pricewatch.bot.dto.BotReply;
import ru.pricewatch.config.BotProperties;
import ru.pricewatch.exception.SourceException;
import ru.pricewatch.exception.UserInputException;

class PriceWatchBotTest {

    private static final long CHAT_ID = 42L;

    private final CommandRouter router = mock(CommandRouter.class);
    private final BotResponder responder = mock(BotResponder.class);
    private final PriceWatchBot bot = new PriceWatchBot(new BotProperties("test-token"), router, responder);

    @Test
    void sendsWhateverTheRouterAnswered() {
        when(router.route(CHAT_ID, "/start")).thenReturn(new BotReply("Привет"));

        bot.consume(textUpdate("/start"));

        verify(responder).sendText(CHAT_ID, "Привет");
    }

    @Test
    void turnsUserInputExceptionIntoItsOwnMessage() {
        when(router.route(anyLong(), anyString())).thenThrow(new UserInputException("Не понял ссылку"));

        bot.consume(textUpdate("http://мусор"));

        verify(responder).sendText(CHAT_ID, "Не понял ссылку");
    }

    @Test
    void answersWithItsOwnTextWhenSourceIsUnavailable() {
        when(router.route(anyLong(), anyString())).thenThrow(new SourceException("Wildberries молчит"));

        bot.consume(textUpdate("12345678"));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(responder).sendText(eq(CHAT_ID), sent.capture());
        assertThat(sent.getValue()).contains("Не смог получить цену");
    }

    /** Заход 1, критерий приёмки: исключение обработчика не должно убивать поллинг. */
    @Test
    void survivesAnyOtherException() {
        when(router.route(anyLong(), anyString())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> bot.consume(textUpdate("/start"))).doesNotThrowAnyException();

        verify(responder).sendText(eq(CHAT_ID), anyString());
    }

    @Test
    void ignoresUpdatesWithoutText() {
        bot.consume(new Update());

        verify(responder, never()).sendText(anyLong(), anyString());
    }

    private static Update textUpdate(String text) {
        Message message = new Message();
        message.setChat(new Chat(CHAT_ID, "private"));
        message.setText(text);

        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
