package ru.pricewatch.source;

import ru.pricewatch.exception.SourceException;

/**
 * Единственный интерфейс проекта: две реализации разной природы (JSON API и HTML-страница)
 * существуют с первого дня [Р4].
 */
public interface PriceSource {

    SourceType type();

    /** Узнаёт ли источник этот ввод пользователя — артикул или ссылку. */
    boolean supports(String userInput);

    /**
     * @throws SourceException если цену получить не удалось
     */
    FetchedPrice fetch(String externalId) throws SourceException;
}
