package ru.pricewatch.bot.dto;

/** Ответ бота на одно сообщение. В Заходе 4 сюда приедут картинка и клавиатура. */
public record BotReply(String text) {}
