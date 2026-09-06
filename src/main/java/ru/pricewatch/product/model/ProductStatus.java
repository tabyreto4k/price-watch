package ru.pricewatch.product.model;

public enum ProductStatus {
    /** Проверяется по расписанию. */
    ACTIVE,
    /** Источник перестал отдавать цену: из проверок исключён. */
    UNAVAILABLE
}
