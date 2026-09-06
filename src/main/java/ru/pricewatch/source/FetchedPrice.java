package ru.pricewatch.source;

import java.math.BigDecimal;

/** Что источник вернул за один поход: название товара и текущая цена. */
public record FetchedPrice(String title, BigDecimal price) {}
