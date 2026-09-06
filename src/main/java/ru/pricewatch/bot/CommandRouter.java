package ru.pricewatch.bot;

import org.springframework.stereotype.Component;
import ru.pricewatch.bot.dto.BotReply;

/** Чистая маршрутизация текста в ответ: ни сети, ни состояния. */
@Component
public class CommandRouter {

    private static final String GREETING =
            """
            Привет! Я слежу за ценами.

            Пришли артикул Wildberries или ссылку на страницу товара — я запомню цену \
            и напишу, когда она упадёт.

            Примеры:
            12345678
            https://www.wildberries.ru/catalog/12345678/detail.aspx

            /help — эта справка""";

    private static final String FALLBACK =
            "Не понял. Пришли артикул Wildberries (число) или ссылку на товар. /help покажет примеры.";

    public BotReply route(long chatId, String text) {
        return switch (text.trim()) {
            case "/start", "/help" -> new BotReply(GREETING);
            default -> new BotReply(FALLBACK);
        };
    }
}
