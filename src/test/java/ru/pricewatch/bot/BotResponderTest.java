package ru.pricewatch.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.pricewatch.bot.dto.BotButton;
import ru.pricewatch.bot.dto.BotReply;

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
    void sendsPhotoWithCaptionAndButtons() throws Exception {
        byte[] png = {1, 2, 3};

        responder.send(42L, BotReply.photo(png, "Кружка", List.of(List.of(BotButton.chart(12L)))));

        ArgumentCaptor<SendPhoto> sent = ArgumentCaptor.forClass(SendPhoto.class);
        verify(telegramClient).execute(sent.capture());
        assertThat(sent.getValue().getCaption()).isEqualTo("Кружка");
        assertThat(sent.getValue().getPhoto().isNew()).isTrue();
        assertThat(keyboardData(sent.getValue().getReplyMarkup())).containsExactly("chart:12");
    }

    @Test
    void attachesButtonsToTextMessages() throws Exception {
        responder.send(42L, BotReply.message("Слежу за ценами:", List.of(List.of(BotButton.unsubscribe(12L)))));

        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(sent.capture());
        assertThat(keyboardData(sent.getValue().getReplyMarkup())).containsExactly("drop:12");
    }

    /** Пустая клавиатура — это её отсутствие, а не пустой блок кнопок в сообщении. */
    @Test
    void sendsNoKeyboardWhenThereAreNoButtons() throws Exception {
        responder.sendText(42L, "Привет");

        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(sent.capture());
        assertThat(sent.getValue().getReplyMarkup()).isNull();
    }

    @Test
    void confirmsCallbackQuery() throws Exception {
        responder.answerCallback("cb-1");

        ArgumentCaptor<AnswerCallbackQuery> sent = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(telegramClient).execute(sent.capture());
        assertThat(sent.getValue().getCallbackQueryId()).isEqualTo("cb-1");
    }

    @Test
    void swallowsFailedCallbackConfirmation() throws Exception {
        doThrow(new TelegramApiException("Bot API молчит"))
                .when(telegramClient)
                .execute(any(AnswerCallbackQuery.class));

        assertThatCode(() -> responder.answerCallback("cb-1")).doesNotThrowAnyException();
    }

    @Test
    void swallowsDeliveryFailure() throws Exception {
        doThrow(new TelegramApiException("Bot API молчит")).when(telegramClient).execute(any(SendMessage.class));

        assertThatCode(() -> responder.sendText(42L, "Привет")).doesNotThrowAnyException();
    }

    private static List<String> keyboardData(Object markup) {
        return ((InlineKeyboardMarkup) markup)
                .getKeyboard().stream()
                        .flatMap(List::stream)
                        .map(button -> button.getCallbackData())
                        .toList();
    }
}
