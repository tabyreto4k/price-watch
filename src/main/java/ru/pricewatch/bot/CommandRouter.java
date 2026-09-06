package ru.pricewatch.bot;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.pricewatch.bot.dto.BotReply;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.product.service.ProductService;
import ru.pricewatch.source.FetchedPrice;
import ru.pricewatch.source.ResolvedInput;
import ru.pricewatch.source.SourceRouter;
import ru.pricewatch.subscription.service.SubscriptionService;

/** Транспорт: разобрать ввод, позвать сервисы, отформатировать ответ. Без бизнес-логики. */
@Component
public class CommandRouter {

    private static final String GREETING =
            """
            Привет! Я слежу за ценами.

            Пришли артикул Wildberries или ссылку на страницу товара — я запомню цену \
            и напишу, когда она упадёт.

            Примеры:
            12345678
            https://www.wildberries.ru/catalog/12345678/detail.aspx

            /help — эта справка""";

    private final SourceRouter sourceRouter;
    private final ProductService productService;
    private final SubscriptionService subscriptionService;

    public CommandRouter(
            SourceRouter sourceRouter, ProductService productService, SubscriptionService subscriptionService) {
        this.sourceRouter = sourceRouter;
        this.productService = productService;
        this.subscriptionService = subscriptionService;
    }

    public BotReply route(long chatId, String text) {
        String input = text.trim();
        return switch (input) {
            case "/start", "/help" -> new BotReply(GREETING);
            default -> subscribe(chatId, input);
        };
    }

    private BotReply subscribe(long chatId, String input) {
        ResolvedInput resolved = sourceRouter.resolve(input);
        FetchedPrice fetched = sourceRouter.fetch(resolved);

        Product product = productService.registerOrGet(resolved.type(), resolved.externalId(), fetched);
        subscriptionService.subscribe(chatId, product);

        return new BotReply("Слежу: %s, сейчас %s ₽".formatted(product.getTitle(), price(product.getLastPrice())));
    }

    private static String price(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symbols).format(value);
    }
}
