package ru.pricewatch.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

class BotResponderTest {

    private final TelegramClient telegramClient = mock(TelegramClient.class);
    private final BotResponder responder = new BotResponder(telegramClient);

    @Test
    void sendsTextToTheGivenChat() throws Exception {
        responder.sendText(42L, "Привет");

        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(sent.capture());
        assertThat(sent.getValue().getChatId()).isEqualTo("42");
        assertThat(sent.getValue().getText()).isEqualTo("Привет");
    }

    @Test
    void swallowsDeliveryFailure() throws Exception {
        doThrow(new TelegramApiException("Bot API молчит")).when(telegramClient).execute(any(SendMessage.class));

        assertThatCode(() -> responder.sendText(42L, "Привет")).doesNotThrowAnyException();
    }
}
