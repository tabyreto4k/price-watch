package ru.pricewatch.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.pricewatch.config.BotProperties;
import ru.pricewatch.exception.UserInputException;

/**
 * Транспорт: получает update, отдаёт его роутеру, отвечает пользователю. Здесь же —
 * единственный catch проекта: упавший обработчик не должен останавливать поллинг.
 */
@Component
public class PriceWatchBot extends DefaultLongPollingUpdateConsumer implements SpringLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(PriceWatchBot.class);

    private static final String INTERNAL_ERROR = "Что-то пошло не так на моей стороне. Попробуй ещё раз через минуту.";

    private final BotProperties properties;
    private final CommandRouter router;
    private final BotResponder responder;

    public PriceWatchBot(BotProperties properties, CommandRouter router, BotResponder responder) {
        this.properties = properties;
        this.router = router;
        this.responder = responder;
    }

    @Override
    public String getBotToken() {
        return properties.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        long chatId = update.getMessage().getChatId();
        try {
            responder.sendText(
                    chatId, router.route(update.getMessage().getText()).text());
        } catch (UserInputException e) {
            log.warn("Не разобрали ввод из чата {}: {}", chatId, e.getMessage());
            responder.sendText(chatId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Обработчик упал на сообщении из чата {}", chatId, e);
            responder.sendText(chatId, INTERNAL_ERROR);
        }
    }
}
