package ru.pricewatch.bot;

import java.util.Arrays;
import ru.pricewatch.exception.UserInputException;

/**
 * Нажатие на кнопку: действие и подписка, к которой оно относится. Telegram отдаёт
 * назад ровно ту строку, что была в кнопке, — формат читается и пишется в одном месте.
 */
public record Callback(Action action, long subscriptionId) {

    private static final String SEPARATOR = ":";
    private static final String STALE_BUTTON = "Кнопка устарела — открой /list заново.";

    public String data() {
        return action.prefix + SEPARATOR + subscriptionId;
    }

    public static Callback parse(String data) {
        String[] parts = data.split(SEPARATOR, 2);
        if (parts.length != 2) {
            throw new UserInputException(STALE_BUTTON);
        }
        Action action = Arrays.stream(Action.values())
                .filter(candidate -> candidate.prefix.equals(parts[0]))
                .findFirst()
                .orElseThrow(() -> new UserInputException(STALE_BUTTON));
        try {
            return new Callback(action, Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            throw new UserInputException(STALE_BUTTON);
        }
    }

    public enum Action {
        CHART("chart"),
        UNSUBSCRIBE("drop");

        private final String prefix;

        Action(String prefix) {
            this.prefix = prefix;
        }
    }
}
