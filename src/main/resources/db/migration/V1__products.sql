CREATE TABLE products (
    id          BIGSERIAL      PRIMARY KEY,
    source      VARCHAR(32)    NOT NULL,
    external_id VARCHAR(512)   NOT NULL,
    title       VARCHAR(512)   NOT NULL,
    last_price  NUMERIC(12, 2) NOT NULL,
    status      VARCHAR(16)    NOT NULL,

    -- Один и тот же артикул у разных источников — разные товары.
    CONSTRAINT products_source_external_id_key UNIQUE (source, external_id),
    CONSTRAINT products_last_price_positive CHECK (last_price > 0)
);
