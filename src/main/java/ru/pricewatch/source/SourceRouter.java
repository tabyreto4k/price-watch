package ru.pricewatch.source;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.pricewatch.exception.UserInputException;

/** Ввод пользователя → источник, который его понимает. */
@Component
public class SourceRouter {

    private static final String DONT_UNDERSTAND =
            "Не понял. Пришли артикул Wildberries (6–9 цифр) или ссылку на страницу товара.";

    private final List<PriceSource> sources;

    public SourceRouter(List<PriceSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public ResolvedInput resolve(String userInput) {
        String input = userInput.trim();
        return sources.stream()
                .filter(source -> source.supports(input))
                .findFirst()
                .map(source -> new ResolvedInput(source.type(), input))
                .orElseThrow(() -> new UserInputException(DONT_UNDERSTAND));
    }

    public FetchedPrice fetch(ResolvedInput resolved) {
        return sources.stream()
                .filter(source -> source.type() == resolved.type())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Нет источника типа " + resolved.type()))
                .fetch(resolved.externalId());
    }
}
