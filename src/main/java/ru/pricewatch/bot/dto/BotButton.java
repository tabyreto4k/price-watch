package ru.pricewatch.bot.dto;

import ru.pricewatch.bot.Callback;

/** Кнопка под сообщением: подпись для человека и полезная нагрузка для роутера. */
public record BotButton(String label, String callbackData) {

    public static BotButton chart(long subscriptionId) {
        return new BotButton("📈 График", new Callback(Callback.Action.CHART, subscriptionId).data());
    }

    public static BotButton unsubscribe(long subscriptionId) {
        return new BotButton("🗑 Удалить", new Callback(Callback.Action.UNSUBSCRIBE, subscriptionId).data());
    }
}
