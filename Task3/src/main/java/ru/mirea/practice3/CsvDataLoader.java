package ru.mirea.practice3;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.mirea.practice3.Domain.*;

/** Загружает четыре CSV из предыдущей практической без обращения к БД. */
final class CsvDataLoader {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvDataLoader() { }

    static DataSet load(Path directory) throws IOException {
        Map<Integer, Darkstore> darkstores = new HashMap<>();
        Map<Integer, Good> goods = new HashMap<>();
        Map<Integer, Delivery> deliveries = new HashMap<>();
        List<GoodsListItem> items = new ArrayList<>();

        for (List<String> row : readRows(directory.resolve("darkstore.csv"),
                List.of("id", "address"))) {
            Darkstore darkstore = new Darkstore(Integer.parseInt(row.get(0)), row.get(1));
            requireUnique(darkstores, darkstore.id(), darkstore, "darkstore");
        }
        for (List<String> row : readRows(directory.resolve("good.csv"),
                List.of("id", "name", "price"))) {
            Good good = new Good(Integer.parseInt(row.get(0)), row.get(1),
                    new BigDecimal(row.get(2)));
            requireUnique(goods, good.id(), good, "good");
        }
        for (List<String> row : readRows(directory.resolve("delivery.csv"),
                List.of("id", "id_darkstore", "datetime_delivery", "status"))) {
            Delivery delivery = new Delivery(Integer.parseInt(row.get(0)),
                    Integer.parseInt(row.get(1)),
                    LocalDateTime.parse(row.get(2), DATE), row.get(3));
            requireUnique(deliveries, delivery.id(), delivery, "delivery");
        }
        for (List<String> row : readRows(directory.resolve("goods_list.csv"),
                List.of("id_good", "id_delivery", "count"))) {
            items.add(new GoodsListItem(Integer.parseInt(row.get(0)),
                    Integer.parseInt(row.get(1)), Integer.parseInt(row.get(2))));
        }
        return new DataSet(Map.copyOf(darkstores), Map.copyOf(goods),
                Map.copyOf(deliveries), List.copyOf(items));
    }

    private static <T> void requireUnique(Map<Integer, T> map, int id,
                                           T value, String table) {
        if (map.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("Повторяющийся ID " + id + " в " + table);
        }
    }

    private static List<List<String>> readRows(Path file, List<String> expectedHeader)
            throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IOException("CSV без заголовка: " + file);
        }
        List<String> actualHeader = parseCsvRow(lines.get(0).replaceFirst("^\\uFEFF", ""));
        if (!actualHeader.equals(expectedHeader)) {
            throw new IOException("Неверный заголовок CSV " + file + ": " + actualHeader);
        }
        List<List<String>> result = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            List<String> fields = parseCsvRow(lines.get(index));
            if (fields.size() != expectedHeader.size()) {
                throw new IOException("Неверное число полей: " + file +
                        ", строка " + (index + 1));
            }
            result.add(fields);
        }
        return result;
    }

    /** Учитываем запятые в адресах и удвоенные кавычки в названиях товаров. */
    static List<String> parseCsvRow(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        afterQuotes = true;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == ',') {
                fields.add(field.toString().trim());
                field.setLength(0);
                afterQuotes = false;
            } else if (c == '"') {
                if (afterQuotes || !field.toString().isBlank()) {
                    throw new IllegalArgumentException("Некорректные кавычки в CSV: " + line);
                }
                field.setLength(0);
                inQuotes = true;
            } else if (afterQuotes) {
                if (!Character.isWhitespace(c)) {
                    throw new IllegalArgumentException("Ожидалась запятая после кавычек: " + line);
                }
            } else {
                field.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Незакрытые кавычки в CSV: " + line);
        }
        fields.add(field.toString().trim());
        return fields;
    }
}
