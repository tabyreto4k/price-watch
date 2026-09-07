package ru.pricewatch.bot;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.pricewatch.bot.dto.BotButton;
import ru.pricewatch.bot.dto.BotReply;
import ru.pricewatch.chart.PriceChartRenderer;
import ru.pricewatch.exception.UserInputException;
import ru.pricewatch.product.model.PricePoint;
import ru.pricewatch.product.model.Product;
import ru.pricewatch.product.model.ProductStatus;
import ru.pricewatch.product.service.ProductService;
import ru.pricewatch.source.FetchedPrice;
import ru.pricewatch.source.ResolvedInput;
import ru.pricewatch.source.SourceRouter;
import ru.pricewatch.subscription.model.Subscription;
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

            /list — подписки, графики и удаление
            порог 10 — писать только о снижении от 10%
            /help — эта справка""";

    private static final String NOTHING_TRACKED =
            "Пока ни за чем не слежу. Пришли артикул Wildberries или ссылку на товар.";

    private static final String THRESHOLD_HINT = "Напиши «порог 10», если писать только о снижении от 10%.";

    private static final String THRESHOLD_RANGE = "Порог — целое число от 1 до 100. Например: порог 10";

    /** Порог набирают руками, поэтому и «Порог», и лишние пробелы — нормальный ввод. */
    private static final Pattern THRESHOLD_COMMAND =
            Pattern.compile("^порог\\s+(\\d{1,3})$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Длина ограничена: id подписки длиннее 18 цифр не бывает, а `parseLong` на таком бросает. */
    private static final Pattern CHART_COMMAND = Pattern.compile("^/chart_(\\d{1,18})$");

    private final SourceRouter sourceRouter;
    private final ProductService productService;
    private final SubscriptionService subscriptionService;
    private final PriceChartRenderer chartRenderer;
    private final PriceFormatter priceFormatter;
    private final Duration chartWindow;

    public CommandRouter(
            SourceRouter sourceRouter,
            ProductService productService,
            SubscriptionService subscriptionService,
            PriceChartRenderer chartRenderer,
            PriceFormatter priceFormatter,
            @Value("${pricewatch.chart.history-window}") Duration chartWindow) {
        this.sourceRouter = sourceRouter;
        this.productService = productService;
        this.subscriptionService = subscriptionService;
        this.chartRenderer = chartRenderer;
        this.priceFormatter = priceFormatter;
        this.chartWindow = chartWindow;
    }

    public BotReply route(long chatId, String text) {
        String input = text.trim();

        Matcher threshold = THRESHOLD_COMMAND.matcher(input);
        if (threshold.matches()) {
            return setThreshold(chatId, threshold.group(1));
        }
        Matcher chart = CHART_COMMAND.matcher(input);
        if (chart.matches()) {
            return chart(chatId, Long.parseLong(chart.group(1)));
        }

        return switch (input) {
            case "/start", "/help" -> BotReply.message(GREETING);
            case "/list" -> list(chatId);
            default -> subscribe(chatId, input);
        };
    }

    public BotReply routeCallback(long chatId, String callbackData) {
        Callback callback = Callback.parse(callbackData);
        return switch (callback.action()) {
            case CHART -> chart(chatId, callback.subscriptionId());
            case UNSUBSCRIBE -> unsubscribe(chatId, callback.subscriptionId());
        };
    }

    private BotReply subscribe(long chatId, String input) {
        ResolvedInput resolved = sourceRouter.resolve(input);
        FetchedPrice fetched = sourceRouter.fetch(resolved);

        Product product = productService.registerOrGet(resolved.type(), resolved.externalId(), fetched);
        subscriptionService.subscribe(chatId, product);

        return BotReply.message("Слежу: %s, сейчас %s ₽\n%s"
                .formatted(product.getTitle(), priceFormatter.format(product.getLastPrice()), THRESHOLD_HINT));
    }

    private BotReply list(long chatId) {
        List<Subscription> subscriptions = subscriptionService.listByChat(chatId);
        if (subscriptions.isEmpty()) {
            return BotReply.message(NOTHING_TRACKED);
        }

        StringBuilder text = new StringBuilder("Слежу за ценами:");
        List<List<BotButton>> keyboard = new ArrayList<>();
        for (Subscription subscription : subscriptions) {
            text.append("\n\n").append(describe(subscription));
            keyboard.add(List.of(BotButton.chart(subscription.getId()), BotButton.unsubscribe(subscription.getId())));
        }
        return BotReply.message(text.toString(), keyboard);
    }

    private BotReply chart(long chatId, long subscriptionId) {
        Product product =
                subscriptionService.requireOwned(chatId, subscriptionId).getProduct();
        List<PricePoint> history = productService.chartHistory(product, chartWindow);

        return BotReply.photo(
                chartRenderer.render(product.getTitle(), history),
                caption(product, history),
                List.of(List.of(BotButton.unsubscribe(subscriptionId))));
    }

    private BotReply unsubscribe(long chatId, long subscriptionId) {
        Subscription removed = subscriptionService.unsubscribe(chatId, subscriptionId);
        return BotReply.message(
                "Больше не слежу за «%s».".formatted(removed.getProduct().getTitle()));
    }

    private BotReply setThreshold(long chatId, String percent) {
        int value = Integer.parseInt(percent);
        if (value < 1 || value > 100) {
            throw new UserInputException(THRESHOLD_RANGE);
        }
        Subscription subscription = subscriptionService.setThreshold(chatId, value);
        return BotReply.message("Порог %d%% для «%s»: напишу, когда цена упадёт хотя бы настолько."
                .formatted(value, subscription.getProduct().getTitle()));
    }

    private String describe(Subscription subscription) {
        Product product = subscription.getProduct();
        StringBuilder line = new StringBuilder("#%d %s\n%s ₽"
                .formatted(subscription.getId(), product.getTitle(), priceFormatter.format(product.getLastPrice())));

        productService
                .previousPrice(product)
                .ifPresent(previous -> line.append(" %s было %s ₽"
                        .formatted(arrow(previous, product.getLastPrice()), priceFormatter.format(previous))));
        if (subscription.getThresholdPercent() != null) {
            line.append(" · порог ").append(subscription.getThresholdPercent()).append('%');
        }
        if (product.getStatus() == ProductStatus.UNAVAILABLE) {
            line.append("\n⚠ источник перестал отдавать цену");
        }
        return line.toString();
    }

    private String caption(Product product, List<PricePoint> history) {
        List<BigDecimal> prices = history.stream()
                .map(PricePoint::getPrice)
                .sorted(Comparator.naturalOrder())
                .toList();
        return "%s\nсейчас %s ₽ · мин %s ₽ · макс %s ₽ за последние %d дней"
                .formatted(
                        product.getTitle(),
                        priceFormatter.format(product.getLastPrice()),
                        priceFormatter.format(prices.get(0)),
                        priceFormatter.format(prices.get(prices.size() - 1)),
                        chartWindow.toDays());
    }

    private static String arrow(BigDecimal previous, BigDecimal current) {
        return current.compareTo(previous) < 0 ? "↓" : "↑";
    }
}
