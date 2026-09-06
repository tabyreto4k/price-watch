package ru.pricewatch;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Общая база интеграционных тестов: один Postgres на весь прогон и одна конфигурация
 * контекста, чтобы Spring кэшировал его между классами.
 *
 * <p>Контейнер поднимается статическим блоком, а не парой {@code @Testcontainers} +
 * {@code @Container}: та останавливает статическое поле после КАЖДОГО класса-наследника,
 * и второй класс уже не находит базу. Гасит контейнер Ryuk на выходе из JVM.
 */
@SpringBootTest
@ActiveProfiles("it")
public abstract class AbstractPostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
