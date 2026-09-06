package ru.pricewatch.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Единственная точка, откуда проект зовёт Bot API наружу. */
@Component
public class BotResponder {

    private static final Logger log = LoggerFactory.getLogger(BotResponder.class);

    private final TelegramClient telegramClient;

    public BotResponder(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void sendText(long chatId, String text) {
        try {
            telegramClient.execute(
                    SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            // Пробрасывать некуда: этим же методом отвечают на ошибку обработчика.
            log.error("Не отправили сообщение в чат {}", chatId, e);
        }
    }
}
