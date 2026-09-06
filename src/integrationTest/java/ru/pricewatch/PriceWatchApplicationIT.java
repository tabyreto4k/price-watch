package ru.pricewatch;

import org.junit.jupiter.api.Test;

/** Контекст поднимается, Flyway накатывает схему, Hibernate её принимает (`ddl-auto: validate`). */
class PriceWatchApplicationIT extends AbstractPostgresIT {

    @Test
    void contextLoads() {}
}
