CREATE TABLE subscriptions (
    id                BIGSERIAL PRIMARY KEY,
    chat_id           BIGINT    NOT NULL,
    product_id        BIGINT    NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    -- NULL — уведомлять о любом снижении; иначе минимальный процент снижения.
    threshold_percent INTEGER,

    CONSTRAINT subscriptions_chat_product_key UNIQUE (chat_id, product_id),
    CONSTRAINT subscriptions_threshold_range
        CHECK (threshold_percent IS NULL OR (threshold_percent BETWEEN 1 AND 100))
);

-- Рассылка алерта идёт от товара ко всем его подписчикам.
CREATE INDEX subscriptions_product_idx ON subscriptions (product_id);
