package ru.pricewatch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Контекст поднимается: бины бота собираются, проперти читаются. */
@SpringBootTest
@ActiveProfiles("it")
class PriceWatchApplicationIT {

    @Test
    void contextLoads() {}
}
