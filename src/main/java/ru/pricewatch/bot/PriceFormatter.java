package ru.pricewatch.bot;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** «1990.00» → «1 990». Один формат цены на ответы бота и на тексты уведомлений. */
@Component
public class PriceFormatter {

    public String format(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        // DecimalFormat не потокобезопасен, а джоб и бот ходят сюда из разных потоков.
        return new DecimalFormat("#,##0.##", symbols).format(value);
    }
}
