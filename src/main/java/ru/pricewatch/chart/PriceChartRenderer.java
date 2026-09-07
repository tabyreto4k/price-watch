package ru.pricewatch.chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Date;
import java.util.List;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.springframework.stereotype.Component;
import ru.pricewatch.product.model.PricePoint;

/** История цены → PNG в память: временные файлы боту не нужны [Р7]. */
@Component
public class PriceChartRenderer {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 400;

    /** Постоянная цена даёт вырожденный диапазон, и ось расписывается по копейкам. */
    private static final double MIN_RANGE_RUB = 10.0;

    public byte[] render(String title, List<PricePoint> points) {
        TimeSeries series = new TimeSeries("Цена");
        for (PricePoint point : points) {
            // addOrUpdate, а не add: две точки в одной секунде — это дубль периода, на котором add бросает.
            series.addOrUpdate(new Second(Date.from(point.getRecordedAt())), point.getPrice());
        }

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                title, null, "₽", new TimeSeriesCollection(series), false, false, false);
        style(chart);

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            ChartUtils.writeChartAsPNG(png, chart, WIDTH, HEIGHT);
        } catch (IOException e) {
            throw new UncheckedIOException("Не отрисовали график «" + title + "»", e);
        }
        return png.toByteArray();
    }

    private static void style(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // Точки нужны видимыми: у истории из одной записи линии между чем и чем нет.
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, new Color(0x2E, 0x7D, 0x32));
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(renderer);

        NumberAxis priceAxis = (NumberAxis) plot.getRangeAxis();
        // Цены рядом друг с другом: от нуля график был бы почти плоским.
        priceAxis.setAutoRangeIncludesZero(false);
        priceAxis.setAutoRangeMinimumSize(MIN_RANGE_RUB);
    }
}
