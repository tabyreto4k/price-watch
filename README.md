# PriceWatch

[![ci](https://github.com/tabyreto4k/price-watch/actions/workflows/ci.yml/badge.svg)](https://github.com/tabyreto4k/price-watch/actions/workflows/ci.yml)
[![codeql](https://github.com/tabyreto4k/price-watch/actions/workflows/codeql.yml/badge.svg)](https://github.com/tabyreto4k/price-watch/actions/workflows/codeql.yml)
[![release](https://github.com/tabyreto4k/price-watch/actions/workflows/release.yml/badge.svg)](https://github.com/tabyreto4k/price-watch/actions/workflows/release.yml)
[![deploy](https://github.com/tabyreto4k/price-watch/actions/workflows/deploy.yml/badge.svg)](https://github.com/tabyreto4k/price-watch/actions/workflows/deploy.yml)
[![coverage gate](https://img.shields.io/badge/coverage%20gate-70%25-blue)](https://github.com/tabyreto4k/price-watch/actions/workflows/ci.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Telegram-бот, который следит за ценой товара и пишет, когда она упала. Кидаешь ему
артикул Wildberries или ссылку — он запоминает цену, обходит источники по расписанию и
уведомляет о снижении; по кнопке рисует график истории.

Публичной ссылки на бота здесь нет намеренно: это учебный проект, а не сервис, за
которым кто-то должен следить. Поднять свой — [три команды ниже](#запуск-локально), токен
берётся у @BotFather.

Фактическое покрытие печатается в summary каждого прогона `ci`; ниже 70% сборка не
проходит.

## Что умеет

| Ввод | Реакция |
|---|---|
| `/start`, `/help` | Что умеет и примеры ввода |
| артикул WB или ссылка на товар | Подписка: «Слежу: <товар>, сейчас <цена>» |
| `порог 10` | Писать только о снижении от 10% (иначе — о любом) |
| `/list` | Подписки: цена, динамика, порог, кнопки «график» и «удалить» |
| кнопка «график», `/chart_N` | PNG с историей цены, в подписи — мин/макс/сейчас |
| мусор | Дружелюбное «не понял», без стектрейса в чат |

Раз в 30 минут планировщик обходит товары, за которыми кто-то следит, и при снижении
шлёт уведомление — с кнопкой графика.

## Как устроено

```
Telegram Bot API ←── long polling ──┐
                                    │           ┌──→ Wildberries JSON API (артикул)
пользователь ──→ команды ──────→ PriceWatch ────┤
             ←── уведомления ──  (Spring Boot)  └──→ HTML-страница (jsoup + селектор)
             ←── PNG-график ───      │
                                 PostgreSQL (products, price_points,
                                             subscriptions, shedlock)
```

Один сервис, package-by-feature. Транспорт Telegram отделён от домена: `bot/` парсит
ввод и форматирует ответ, бизнес-логика про `telegrambots` не знает. Все вызовы Bot API
наружу идут через единственный `BotResponder`, а любая ошибка обработчика ловится в одной
точке — бот не падает и не роняет поллинг.

## Стек

Java 21 · Spring Boot 3.5 · PostgreSQL 16 + Flyway · telegrambots (long polling) ·
WebClient + reactor Retry · Resilience4j RateLimiter · jsoup · JFreeChart · ShedLock (JDBC) ·
JUnit 5 + AssertJ + Mockito + Testcontainers + MockWebServer · Gradle (Kotlin DSL) ·
Docker · GitHub Actions

## Запуск локально

```bash
cp .env.example .env      # BOT_TOKEN от @BotFather и POSTGRES_PASSWORD
docker compose up -d --wait
```

Готовый образ каждой ревизии `main` лежит в GHCR — им можно поднять тот же стек, не
собирая ничего локально:

```bash
IMAGE_TAG=latest docker compose -f compose.prod.yml up -d --wait
```

Проверка — в dev-компоузе порт 8080 проброшен на localhost (в проде он не публикуется):

```bash
curl -s localhost:8080/actuator/health      # {"status":"UP", ...}
curl -s localhost:8080/actuator/prometheus | head
```

## Разработка

```bash
./gradlew build              # компиляция, spotless, checkstyle, unit-тесты, jacoco
./gradlew integrationTest    # Testcontainers, нужен запущенный Docker
./gradlew spotlessApply      # починить форматирование
```

JDK 21 на машине иметь не обязательно: Gradle-тулчейн скачает нужный сам.

## Деплой

```
push в main ──→ ci (тесты)          ─┐
            ──→ release (образ)      ├──→ deploy ──→ ssh на VPS ──→ compose pull && up -d
                     ↓               ─┘                                      ↓
              ghcr.io/<owner>/price-watch:{latest, sha-<sha>}          healthcheck зелёный
```

`deploy` запускается после `release` и не выкатывает, пока `ci` на том же коммите не
позеленел: образ собирается без тестов, поэтому зелёный релиз сам по себе ничего не
доказывает. Выкатывается не `latest`, а конкретный `sha-<полный sha>` — что выкачено,
видно в логе прогона. `docker compose up --wait` делает healthcheck частью выката:
контейнер не поднялся — деплой красный.

**Откат:** Actions → deploy → Run workflow → тег предыдущей ревизии (`sha-<...>` или
`latest`). Тот же путь, что и обычный выкат, только тег другой.

### Чеклист VPS

1. Docker с плагином compose (v2.17+ — нужен `--wait-timeout`).
2. Пользователь для деплоя в группе `docker`, его открытый ключ — в `~/.ssh/authorized_keys`.
3. Каталог `~/price-watch` — туда деплой кладёт `compose.prod.yml`.
4. `~/price-watch/.env`: `BOT_TOKEN`, `POSTGRES_PASSWORD` (можно `CHECK_INTERVAL`, `LOG_LEVEL`).
5. Секреты репозитория: `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`.

Логинить VPS в GHCR руками не нужно: деплой делает `docker login` токеном прогона и
`docker logout` следом — долгоживущих ключей от реестра на машине не остаётся.

Порт наружу не публикуется вовсе: бот ходит к Telegram сам (long polling), входящих
соединений у него нет. До базы ходит только он, из сети compose.

> Неверный `BOT_TOKEN` — фатальная ошибка конфигурации: контекст не поднимается. Деплой
> это ловит (`--wait` не дождётся healthcheck), но на уже работающей машине контейнер уйдёт
> в цикл перезапусков примерно раз в 8 секунд — docker-backoff тут не спасает, проверено.
> Лечится правкой `.env` и `docker compose up -d`.

## Уважение к источникам

Бот ходит к чужим API, которые ему ничего не должны:

- **Rate limit на источник** (Resilience4j): Wildberries ≤ 1 rps, HTML-страницы ≤ 0.5 rps.
- **Ретраи только там, где они уместны:** экспоненциальный backoff с jitter на 5xx и
  таймауты; 4xx не ретраится вовсе — это ошибка запроса, а не сбой.
- **Таймауты обязательны:** 3 с на соединение, 5 с на чтение.
- **Честный User-Agent** с ссылкой на репозиторий — понятно, кто пришёл и куда писать.
- **Точка истории пишется только при изменении цены:** проверка раз в 30 минут иначе дала
  бы ~17 тыс. одинаковых строк на товар в год.
- **HTML-источник работает по allow-list доменов** с селектором на каждый, а не «скрейпим
  что попало».
- Эндпоинт карточки Wildberries неофициальный. Сменит схему — товар помечается
  `UNAVAILABLE`, подписчики получают одно уведомление, бот живёт дальше.

## Архитектурные решения

| # | Развилка | Решение и почему |
|---|---|---|
| Р1 | Сборка | Один Gradle-модуль. Мультимодуль для одного сервиса — абстракция «на будущее» |
| Р2 | Обновления Telegram | Long polling: webhook требует домен и TLS, это дороже задачи ([ADR 1](docs/adr/0001-long-polling-not-webhook.md)) |
| Р3 | Библиотека | Официальная `telegrambots`: свой клиент — сотни строк инфраструктуры без пользы |
| Р4 | Источники цен | Два источника разной природы за `PriceSource` — единственный интерфейс проекта ([ADR 2](docs/adr/0002-price-source-interface.md)) |
| Р5 | HTTP и ретраи | WebClient + `Retry.backoff` с jitter, ретрай только 5xx и таймаутов |
| Р6 | Rate limit | Resilience4j RateLimiter на источник: декларативно и настраивается конфигом |
| Р7 | График | JFreeChart → `byte[]` PNG без временных файлов; внешний сервис — чужой аптайм ради картинки |
| Р8 | Планировщик | `@Scheduled` + ShedLock (JDBC): Postgres уже есть, новых контейнеров ноль ([ADR 3](docs/adr/0003-shedlock-for-scheduler.md)) |
| Р9 | История и алерты | Точка истории только при изменении; алерт на снижение, порог % — опция подписки |
| Р10 | Деплой | GHCR + ssh `compose pull && up -d`: дешёвый VPS не должен собирать образ |

Три решения расписаны подробно в [`docs/adr/`](docs/adr) — с тем, чем за них платим.

## Что бы улучшил

- **Webhook вместо long polling**, если бота станет мало в одном экземпляре: сейчас два
  инстанса поллинга на один токен отбирали бы обновления друг у друга.
- **Кэш карточек товаров.** Один и тот же артикул у N подписчиков — один товар в базе, но
  при массовых подписках стоило бы кэшировать ответ источника на минуты.
- **Метрики по источникам в Grafana.** Prometheus-эндпоинт уже есть; не хватает панели
  «сколько раз источник не ответил» — сейчас это видно только в логах.
- **Оживление `UNAVAILABLE`-товаров.** Сейчас товар, от которого источник отвернулся,
  из проверок выпадает навсегда; разумно было бы раз в сутки пробовать снова.
- **i18n.** Тексты вшиты по-русски прямо в роутер — для второго языка их пришлось бы
  вынести в ресурсы.

## Лицензия

[MIT](LICENSE)
