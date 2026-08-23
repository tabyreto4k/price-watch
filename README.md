# PriceWatch

[![ci](https://github.com/tabyreto4k/price-watch/actions/workflows/ci.yml/badge.svg)](https://github.com/tabyreto4k/price-watch/actions/workflows/ci.yml)
[![codeql](https://github.com/tabyreto4k/price-watch/actions/workflows/codeql.yml/badge.svg)](https://github.com/tabyreto4k/price-watch/actions/workflows/codeql.yml)
[![release](https://github.com/tabyreto4k/price-watch/actions/workflows/release.yml/badge.svg)](https://github.com/tabyreto4k/price-watch/actions/workflows/release.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Telegram-бот, который следит за ценой товара и присылает уведомление, когда она падает.
Кидаешь боту артикул Wildberries или ссылку — он подписывает тебя на товар, периодически
опрашивает источник и по запросу рисует график истории цены.

> **Статус:** каркас. Домен, источники цен и планировщик — в работе.

## Стек

Java 21 · Spring Boot 3.5 · PostgreSQL 16 + Flyway · telegrambots (long polling) ·
WebClient + Resilience4j · ShedLock · JFreeChart · JUnit 5 + Testcontainers ·
Gradle (Kotlin DSL) · Docker · GitHub Actions

## Запуск

```bash
cp .env.example .env      # заполнить BOT_TOKEN и POSTGRES_PASSWORD
docker compose up -d --wait
```

Готовый образ каждой ревизии `main` лежит в GHCR:

```bash
docker run --rm -p 8080:8080 --env-file .env ghcr.io/tabyreto4k/price-watch:main
```

Проверка:

```bash
curl -s localhost:8080/actuator/health   # {"status":"UP", ...}
curl -s localhost:8080/actuator/prometheus | head
```

## Разработка

```bash
./gradlew build              # компиляция, spotless, checkstyle, unit-тесты, jacoco
./gradlew integrationTest    # Testcontainers, нужен запущенный Docker
./gradlew spotlessApply      # починить форматирование
```

JDK 21 на машине иметь не обязательно: Gradle-тулчейн скачает нужный сам.

## Что здесь стоит посмотреть

Раздел заполняется по мере готовности. План: экспоненциальный backoff и rate limit
к чужому API, `@Scheduled` + ShedLock (корректность при нескольких инстансах),
`BigDecimal` для денег, обработчики бота как транспорт без бизнес-логики.

## Лицензия

[MIT](LICENSE)
