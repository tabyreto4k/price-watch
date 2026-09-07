-- Сколько раз подряд источник не отдал цену. N подряд — товар уходит в UNAVAILABLE,
-- подписчики получают одно уведомление, из проверок он исключается.
ALTER TABLE products ADD COLUMN failure_streak INTEGER NOT NULL DEFAULT 0;

ALTER TABLE products
    ADD CONSTRAINT products_failure_streak_non_negative CHECK (failure_streak >= 0);
