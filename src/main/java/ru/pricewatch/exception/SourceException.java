package ru.pricewatch.exception;

/**
 * Источник не отдал цену: сеть, чужая ошибка или изменившаяся схема ответа.
 * Unchecked намеренно — обрабатывается в тех же двух точках, что и остальные ошибки
 * (обработчик бота и батч проверки цен), а не пробрасывается через всю цепочку.
 */
public class SourceException extends RuntimeException {

    public SourceException(String message) {
        super(message);
    }

    public SourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
