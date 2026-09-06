package ru.pricewatch.source;

/** К какому источнику относится ввод пользователя и под каким идентификатором. */
public record ResolvedInput(SourceType type, String externalId) {}
