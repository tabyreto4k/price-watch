package ru.pricewatch.bot.dto;

import java.util.List;

/**
 * Ответ бота на одно обращение: текст либо картинка с подписью, под ними — кнопки.
 * Что из этого отправлять, решает {@code BotResponder}.
 */
public record BotReply(String text, byte[] photo, List<List<BotButton>> keyboard) {

    public static BotReply message(String text) {
        return new BotReply(text, null, List.of());
    }

    public static BotReply message(String text, List<List<BotButton>> keyboard) {
        return new BotReply(text, null, keyboard);
    }

    public static BotReply photo(byte[] png, String caption, List<List<BotButton>> keyboard) {
        return new BotReply(caption, png, keyboard);
    }

    public boolean hasPhoto() {
        return photo != null;
    }
}
