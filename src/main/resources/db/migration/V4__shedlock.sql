-- Блокировка планировщика [Р8]: при двух инстансах проверка цен выполняется один раз.
-- Имена колонок задаёт сам ShedLock. Колонка без таймзоны намеренно: с usingDbTime()
-- ShedLock кладёт сюда время по UTC и по UTC же сравнивает. Всё, что лезет в эту таблицу
-- мимо ShedLock, обязано делать то же самое, иначе локи не будут освобождаться.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
