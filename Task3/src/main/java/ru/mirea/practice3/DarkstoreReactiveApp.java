package ru.mirea.practice3;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static ru.mirea.practice3.Domain.*;

/**
 * RxJava 3, реактивная обработка данных BootcampLabs.
 */
public final class DarkstoreReactiveApp {
    private DarkstoreReactiveApp() { }

    public static void main(String[] args) {
        boolean quick = List.of(args).contains("--quick");
        boolean onlyBackpressure = List.of(args).contains("--only-backpressure");
        long intervalMs = quick ? 120 : 700;

        try {
            if (!onlyBackpressure) {
                runBusinessPipeline(intervalMs);
            }
            runBackpressureDemo(quick);
        } catch (Exception e) {
            // Ошибка загрузки файлов / неожиданная ошибка не выводит stack trace.
            System.err.println("[ОШИБКА] Ошибка перехвачена: " + e.getMessage());
        }
    }

    private static void runBusinessPipeline(long intervalMs) throws Exception {
        DataSet historical = CsvDataLoader.load(Path.of("data"));
        log("START", "Загружены CSV: darkstore=" + historical.darkstores().size()
                + ", good=" + historical.goods().size()
                + ", delivery=" + historical.deliveries().size()
                + ", goods_list=" + historical.items().size());

        // Новые доставки — тестовые события, не изменяющие исходные CSV.
        Map<Integer, Delivery> deliveries = new HashMap<>(historical.deliveries());
        deliveries.put(21, new Delivery(21, 1,
                LocalDateTime.of(2024, 6, 8, 10, 0), "COMPLETED"));
        deliveries.put(22, new Delivery(22, 5,
                LocalDateTime.of(2024, 6, 8, 10, 5), "DELIVERY"));
        deliveries.put(23, new Delivery(23, 3,
                LocalDateTime.of(2024, 6, 8, 10, 10), "ASSEMBLY"));
        DataSet data = new DataSet(historical.darkstores(), historical.goods(),
                Map.copyOf(deliveries), historical.items());

        List<GoodsListItem> newEvents = List.of(
                new GoodsListItem(1, 21, 3),     // корректное событие
                new GoodsListItem(9, 22, 2),     // корректное событие
                new GoodsListItem(999, 23, 1),   // ошибка: товара 999 нет
                new GoodsListItem(3, 23, 0),     // ошибка: количество 0
                new GoodsListItem(6, 23, 4)      // поток продолжает работу
        );

        // Источник: исторические записи сразу, новые записи — с интервалом.
        Flowable<GoodsListItem> source = Flowable.concat(
                Flowable.fromIterable(data.items())
                        .doOnNext(row -> log("SOURCE", "история: " + row)),
                Flowable.interval(intervalMs, intervalMs, TimeUnit.MILLISECONDS,
                                Schedulers.io())
                        .take(newEvents.size())
                        .map(i -> newEvents.get(i.intValue()))
                        .doOnNext(row -> log("NEW EVENT", "поступила позиция: " + row))
        );

        // Ошибки одной позиции не обрывают весь поток благодаря onErrorResumeNext(Flowable.empty()).
        List<PricedItem> processed = source
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .filter(row -> {
                    if (row.count() <= 0) {
                        log("FILTER", "пропуск невалидного количества: " + row);
                        return false;
                    }
                    return true;
                })
                .flatMap(row -> Flowable.fromCallable(() -> enrich(row, data))
                        .onErrorResumeNext(error -> {
                            log("ERROR", row + " → " + error.getMessage()
                                    + "; обработка остальных продолжается");
                            return Flowable.empty();
                        }))
                .filter(line -> {
                    if ("CANCELLED".equals(line.status())) {
                        log("FILTER", "исключена отменённая доставка #"
                                + line.deliveryId());
                        return false;
                    }
                    return true;
                })
                .map(line -> new PricedItem(line.darkstoreId(), line.address(),
                        line.deliveryId(), line.status(), line.goodName(),
                        line.count(), line.price(),
                        line.price().multiply(BigDecimal.valueOf(line.count()))))
                .doOnNext(line -> log("PROCESSED", String.format(
                        "даркстор %d | доставка %d | %s | %d × %s = %s",
                        line.darkstoreId(), line.deliveryId(), line.goodName(),
                        line.count(), line.price(), line.total())))
                .toList()
                .blockingGet();

        log("AGGREGATION", "groupBy + reduce: итоговая стоимость позиций "
                + "по дарксторам (исключены отменённые доставки)");

        // Агрегирование именно RxJava-операторами groupBy, map и reduce.
        Flowable.fromIterable(processed)
                .groupBy(PricedItem::darkstoreId)
                .flatMapSingle(group -> group
                        .map(PricedItem::total)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .map(total -> new DarkstoreTotal(group.getKey(),
                                data.darkstores().get(group.getKey()).address(), total)))
                .sorted(Comparator.comparingInt(DarkstoreTotal::darkstoreId))
                .blockingForEach(store -> log("TOTAL", String.format(
                        "darkstore=%d | %s | %s руб.", store.darkstoreId(),
                        store.address(), store.total().toPlainString())));

        log("DONE", "Реактивная обработка завершена, прошли фильтры: "
                + processed.size() + " позиций");
    }

    private static PricedItem enrich(GoodsListItem item, DataSet data) {
        Good good = data.goods().get(item.goodId());

        if (good == null) {
            throw new IllegalArgumentException("товар с id=" + item.goodId() + " отсутствует");
        }

        Delivery delivery = data.deliveries().get(item.deliveryId());

        if (delivery == null) {
            throw new IllegalArgumentException("доставка с id=" + item.deliveryId()
                    + " отсутствует");
        }

        Darkstore store = data.darkstores().get(delivery.darkstoreId());

        if (store == null) {
            throw new IllegalArgumentException("даркстор с id=" + delivery.darkstoreId()
                    + " отсутствует");
        }

        if (good.price().signum() < 0) {
            throw new IllegalArgumentException("отрицательная цена для товара " + item.goodId());
        }

        // Стоимость будет рассчитана следующим оператором map().
        return new PricedItem(store.id(), store.address(), delivery.id(),
                delivery.status(), good.name(), item.count(), good.price(),
                BigDecimal.ZERO);
    }

    /**
     * Пункт 8: источник (каждую 1 мс) быстрее обработчика (каждые 25 мс).
     * onBackpressureDrop() сбрасывает избыточные элементы вместо переполнения.
     */
    private static void runBackpressureDemo(boolean quick) {
        log("BACKPRESSURE", "источник: 1 элемент/мс; обработчик: 25 мс/элемент; "
                + "буфер observeOn=8");

        long maxEvents = quick ? 120 : 400;
        AtomicLong produced = new AtomicLong();
        AtomicLong dropped = new AtomicLong();
        List<Long> handled = new ArrayList<>();

        Flowable.interval(0, 1, TimeUnit.MILLISECONDS, Schedulers.computation())
                .take(maxEvents)
                .doOnNext(i -> produced.incrementAndGet())
                .onBackpressureDrop(i -> dropped.incrementAndGet())
                .observeOn(Schedulers.io(), false, 8)
                .doOnNext(i -> {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Потребитель прерван", e);
                    }
                })
                .blockingForEach(handled::add);

        log("BACKPRESSURE", "создано=" + produced.get()
                + ", обработано=" + handled.size()
                + ", отброшено=" + dropped.get());

        if (produced.get() != handled.size() + dropped.get()) {
            log("BACKPRESSURE", "часть элементов ещё учитывалась в буфере");
        }
    }

    private static synchronized void log(String stage, String message) {
        System.out.printf("[%-12s] [%-27s] %s%n", stage,
                Thread.currentThread().getName(), message);
    }
}
