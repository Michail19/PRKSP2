package ru.mirea.practice7.orders;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ru.mirea.practice7.orders.ApiModels.*;

/**
 * Четыре CSV из BootcampLabs. Работает без БД и без Spring-зависимостей.
 * При старте сервера читает файлы из classpath (/data/*.csv).
 */
public final class CsvCatalog {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<Integer, Darkstore> darkstores;
    private final Map<Integer, Good> goods;
    private final Map<Integer, Delivery> deliveries;
    private final List<GoodsListItem> items;

    private CsvCatalog(Map<Integer, Darkstore> darkstores,
                       Map<Integer, Good> goods,
                       Map<Integer, Delivery> deliveries,
                       List<GoodsListItem> items) {
        this.darkstores = Map.copyOf(darkstores);
        this.goods = Map.copyOf(goods);
        this.deliveries = Map.copyOf(deliveries);
        this.items = List.copyOf(items);
    }

    public static CsvCatalog fromResources() throws IOException {
        Map<Integer, Darkstore> darkstores = new HashMap<>();
        Map<Integer, Good> goods = new HashMap<>();
        Map<Integer, Delivery> deliveries = new HashMap<>();
        List<GoodsListItem> items = new ArrayList<>();

        for (List<String> row : readRows("darkstore.csv", List.of("id", "address"))) {
            Darkstore store = new Darkstore(Integer.parseInt(row.get(0)), row.get(1));
            ensureUnique(darkstores, store.id(), store, "darkstore");
        }
        for (List<String> row : readRows("good.csv", List.of("id", "name", "price"))) {
            Good good = new Good(Integer.parseInt(row.get(0)), row.get(1),
                    new BigDecimal(row.get(2)));
            ensureUnique(goods, good.id(), good, "good");
        }
        for (List<String> row : readRows("delivery.csv",
                List.of("id", "id_darkstore", "datetime_delivery", "status"))) {
            Delivery delivery = new Delivery(Integer.parseInt(row.get(0)),
                    Integer.parseInt(row.get(1)), LocalDateTime.parse(row.get(2), DATE),
                    row.get(3));
            ensureUnique(deliveries, delivery.id(), delivery, "delivery");
        }
        for (List<String> row : readRows("goods_list.csv",
                List.of("id_good", "id_delivery", "count"))) {
            items.add(new GoodsListItem(Integer.parseInt(row.get(0)),
                    Integer.parseInt(row.get(1)), Integer.parseInt(row.get(2))));
        }
        // Проверяем ссылки между таблицами, как в реляционной базе данных.
        for (Delivery delivery : deliveries.values()) {
            if (!darkstores.containsKey(delivery.darkstoreId())) {
                throw new IllegalArgumentException("Несуществующий даркстор: " + delivery);
            }
        }
        for (GoodsListItem item : items) {
            if (!goods.containsKey(item.goodId()) || !deliveries.containsKey(item.deliveryId())) {
                throw new IllegalArgumentException("Нарушена ссылка в goods_list: " + item);
            }
        }
        return new CsvCatalog(darkstores, goods, deliveries, items);
    }

    public int darkstoreCount() { return darkstores.size(); }
    public int goodCount() { return goods.size(); }
    public int deliveryCount() { return deliveries.size(); }
    public int goodsListCount() { return items.size(); }

    public Optional<Good> findGood(int id) {
        return Optional.ofNullable(goods.get(id));
    }

    public List<Good> allGoods() {
        return goods.values().stream().sorted(Comparator.comparingInt(Good::id)).toList();
    }

    public Optional<DeliverySummary> summary(int deliveryId) {
        Delivery delivery = deliveries.get(deliveryId);
        if (delivery == null) {
            return Optional.empty();
        }
        Darkstore store = darkstores.get(delivery.darkstoreId());
        List<GoodsListItem> lines = items.stream()
                .filter(item -> item.deliveryId() == deliveryId).toList();
        BigDecimal total = lines.stream()
                .map(line -> goods.get(line.goodId()).price()
                        .multiply(BigDecimal.valueOf(line.count())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(new DeliverySummary(delivery.id(), store.id(), store.address(),
                delivery.status(), lines.size(), total));
    }

    private static <T> void ensureUnique(Map<Integer, T> map, int id, T value, String name) {
        if (map.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("Повторный ID=" + id + " в " + name);
        }
    }

    private static List<List<String>> readRows(String fileName, List<String> expectedHeader)
            throws IOException {
        String path = "data/" + fileName;
        InputStream source = CsvCatalog.class.getClassLoader().getResourceAsStream(path);
        if (source == null) {
            throw new IOException("CSV не найден в resources: " + path);
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(source, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        if (lines.isEmpty()) {
            throw new IOException("Пустой CSV: " + path);
        }
        List<String> header = CsvParser.parseRow(lines.get(0).replaceFirst("^\uFEFF", ""));
        if (!expectedHeader.equals(header)) {
            throw new IOException("Неверный заголовок " + path + ": " + header);
        }
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = CsvParser.parseRow(lines.get(i));
            if (fields.size() != expectedHeader.size()) {
                throw new IOException("Количество полей неверно: " + path + ", строка " + (i + 1));
            }
            rows.add(fields);
        }
        return rows;
    }
}
