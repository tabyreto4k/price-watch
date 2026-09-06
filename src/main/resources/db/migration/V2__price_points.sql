-- Точка пишется только при изменении цены [Р9]: проверка раз в 30 минут дала бы
-- ~17 тыс. одинаковых строк на товар в год.
CREATE TABLE price_points (
    id          BIGSERIAL      PRIMARY KEY,
    product_id  BIGINT         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    price       NUMERIC(12, 2) NOT NULL,
    recorded_at TIMESTAMPTZ    NOT NULL,

    CONSTRAINT price_points_price_positive CHECK (price > 0)
);

-- График читает историю одного товара по возрастанию времени.
CREATE INDEX price_points_product_recorded_at_idx ON price_points (product_id, recorded_at);
