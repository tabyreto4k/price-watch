package ru.pricewatch.bot;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.pricewatch.bot.dto.BotButton;
import ru.pricewatch.bot.dto.BotReply;

/** Единственная точка, откуда проект зовёт Bot API наружу. */
@Component
public class BotResponder {

    private static final Logger log = LoggerFactory.getLogger(BotResponder.class);

    private static final String CHART_FILE_NAME = "price-history.png";

    private final TelegramClient telegramClient;

    public BotResponder(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void send(long chatId, BotReply reply) {
        try {
            if (reply.hasPhoto()) {
                telegramClient.execute(SendPhoto.builder()
                        .chatId(chatId)
                        .photo(new InputFile(new ByteArrayInputStream(reply.photo()), CHART_FILE_NAME))
                        .caption(reply.text())
                        .replyMarkup(markup(reply.keyboard()))
                        .build());
            } else {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(reply.text())
                        .replyMarkup(markup(reply.keyboard()))
                        .build());
            }
        } catch (TelegramApiException e) {
            // Пробрасывать некуда: этим же методом отвечают на ошибку обработчика.
            log.error("Не отправили сообщение в чат {}", chatId, e);
        }
    }

    public void sendText(long chatId, String text) {
        send(chatId, BotReply.message(text));
    }

    /** Пока нажатие не подтверждено, Telegram крутит часики на кнопке. */
    public void answerCallback(String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Не подтвердили нажатие кнопки {}", callbackQueryId, e);
        }
    }

    private static InlineKeyboardMarkup markup(List<List<BotButton>> keyboard) {
        if (keyboard.isEmpty()) {
            return null;
        }
        return InlineKeyboardMarkup.builder()
                .keyboard(keyboard.stream().map(BotResponder::row).toList())
                .build();
    }

    private static InlineKeyboardRow row(List<BotButton> buttons) {
        return new InlineKeyboardRow(buttons.stream()
                .map(button -> InlineKeyboardButton.builder()
                        .text(button.label())
                        .callbackData(button.callbackData())
                        .build())
                .toList());
    }
}
