package ru.pricewatch.exception;

/** Пользователь прислал то, чего мы не понимаем. Текст исключения уходит прямо в чат. */
public class UserInputException extends RuntimeException {

    public UserInputException(String message) {
        super(message);
    }
}
