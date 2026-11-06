package ru.mirea.practice7.orders;

import java.util.ArrayList;
import java.util.List;

/** Простое чтение строк CSV, включая кавычки и запятые в значениях. */
public final class CsvParser {
    private CsvParser() { }

    public static List<String> parseRow(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        value.append('"');
                        i++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else {
                    value.append(c);
                }
            } else if (c == ',') {
                values.add(value.toString().trim());
                value.setLength(0);
                afterQuote = false;
            } else if (c == '"') {
                if (afterQuote || !value.toString().isBlank()) {
                    throw new IllegalArgumentException("Недопустимые кавычки: " + line);
                }
                value.setLength(0);
                quoted = true;
            } else if (afterQuote) {
                if (!Character.isWhitespace(c)) {
                    throw new IllegalArgumentException("После закрытия кавычек ожидалась запятая: " + line);
                }
            } else {
                value.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Незакрытые кавычки: " + line);
        }
        values.add(value.toString().trim());
        return values;
    }
}
