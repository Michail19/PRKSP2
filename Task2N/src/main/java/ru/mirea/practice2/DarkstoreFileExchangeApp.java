package ru.mirea.practice2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;


public final class DarkstoreFileExchangeApp {
    private static final Path INCOMING = Path.of("data", "incoming");
    private static final Path PROCESSED = Path.of("data", "processed");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DELIVERY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DarkstoreFileExchangeApp() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        boolean once = Arrays.asList(args).contains("--once");
        System.out.println("=== Практическая №2: Java NIO и обмен данными АС Поставщиков ===");

        createDirectories();
        printDirectoryContents();

        // Регистрируем наблюдатель до начальной обработки, чтобы не упустить
        // поступление новых файлов между первым поиском и запуском WatchService.
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            INCOMING.register(watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            processExistingFiles();

            if (once) {
                System.out.println("\nРежим --once: начальная обработка завершена.");
                return;
            }

            watchIncomingDirectory(watcher);
        }
    }

    /** Пункт 1: проверяем наличие и создаём каталоги средствами Path/Files. */
    private static void createDirectories() throws IOException {
        boolean incomingExists = Files.isDirectory(INCOMING);
        boolean processedExists = Files.isDirectory(PROCESSED);

        Files.createDirectories(INCOMING);
        Files.createDirectories(PROCESSED);

        System.out.printf("Каталог %s: %s%n", INCOMING, incomingExists ? "уже существует" : "создан");
        System.out.printf("Каталог %s: %s%n", PROCESSED, processedExists ? "уже существует" : "создан");
    }

    /** Пункт 1: имя, размер и время изменения каждого обычного файла. */
    private static void printDirectoryContents() throws IOException {
        System.out.printf("%nСодержимое %s:%n", INCOMING.toAbsolutePath());

        try (DirectoryStream<Path> files = Files.newDirectoryStream(INCOMING)) {
            int count = 0;
            for (Path file : files) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }

                count++;

                String changed = FILE_DATE_FORMAT.format(Files.getLastModifiedTime(file).toInstant());
                System.out.printf("  %-22s | %6d байт | изменён: %s%n", file.getFileName(), Files.size(file), changed);
            }

            if (count == 0) {
                System.out.println("  (каталог пуст)");
            }
        }
    }

    /** Пункт 2: ищем CSV-файлы; имена могут отличаться от названий таблиц. */
    private static List<Path> findCsvFiles() throws IOException {
        try (Stream<Path> stream = Files.list(INCOMING)) {
            return stream.filter(Files::isRegularFile)
                    .filter(DarkstoreFileExchangeApp::isCsv)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static boolean isCsv(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private static void processExistingFiles() throws IOException {
        List<Path> files = findCsvFiles();
        System.out.printf("%nНайдено входных CSV: %d%n", files.size());
        boolean channelUsed = false;

        for (Path file : files) {
            boolean useChannel = !channelUsed;
            if (processFile(file, useChannel) && useChannel) {
                channelUsed = true;
            }
        }
    }

    /**
     * Пункты 3, 4, 6: читаем → проверяем данные → считаем SHA-256 → перемещаем.
     * При ошибке сохраняем исходный CSV в incoming для исправления.
     */
    private static boolean processFile(Path source, boolean useChannel) {
        if (!isCsv(source) || !Files.isRegularFile(source)) {
            return false;
        }

        System.out.printf("%n--- Обработка файла %s ---%n", source.getFileName());

        try {
            long size = Files.size(source);
            List<String> lines = useChannel
                    ? readUsingFileChannel(source)
                    : Files.readAllLines(source, StandardCharsets.UTF_8);
            ProcessingResult result = validateAndSummarize(lines);
            String checksum = calculateSha256(source);

            // Не перезаписываем уже обработанные файлы: добавляем номер к имени.
            Path destination = freeDestination(source.getFileName().toString());
            Files.move(source, destination);

            System.out.printf("File: %s%n", source.getFileName());
            System.out.printf("Entity: %s%n", result.kind().tableName);
            System.out.printf("Size: %d bytes%n", size);
            System.out.printf("SHA-256: %s%n", checksum);
            System.out.printf("Records: %d%n", result.rows());
            System.out.printf("Result: %s%n", result.summary());
            System.out.println("Status: processed");
            System.out.println("Перемещён в: " + destination);

            return true;
        }
        catch (IOException | IllegalArgumentException e) {
            System.err.printf("Status: error | %s | %s%n", source.getFileName(), e.getMessage());
            return false;
        }
    }

    private static Path freeDestination(String sourceName) throws IOException {
        Path desired = PROCESSED.resolve(sourceName);

        if (!Files.exists(desired)) {
            return desired;
        }

        String base = sourceName.substring(0, sourceName.length() - 4);
        for (int number = 2; number <= 9999; number++) {
            Path candidate = PROCESSED.resolve(base + "_" + number + ".csv");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }

        throw new IOException("Не удалось подобрать свободное имя в processed");
    }

    /**
     * Пункт 5: читаем один из исходных CSV через FileChannel + ByteBuffer.
     * Собираем все байты до UTF-8 декодирования (символ может пересекать блоки).
     */
    private static List<String> readUsingFileChannel(Path file) throws IOException {
        System.out.println("NIO: чтение через FileChannel + ByteBuffer");

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);

            while (channel.read(buffer) != -1) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.write(bytes);
                buffer.clear();
            }

            return List.of(output.toString(StandardCharsets.UTF_8).split("\\R", -1));
        }
    }

    /** CSV-разбор поддерживает запятые внутри кавычек и удвоенные кавычки. */
    private static List<String> parseCsvRow(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    }
                    else {
                        quoted = false;
                        closedQuote = true;
                    }
                }
                else {
                    field.append(c);
                }
            }
            else if (c == ',') {
                fields.add(field.toString().trim());
                field.setLength(0);
                closedQuote = false;
            }
            else if (c == '"') {
                if (!closedQuote && field.toString().isBlank()) {
                    field.setLength(0);
                    quoted = true;
                }
                else {
                    throw new IllegalArgumentException("Некорректное экранирование кавычек в CSV");
                }
            }
            else if (closedQuote) {
                if (!Character.isWhitespace(c)) {
                    throw new IllegalArgumentException("После закрывающей кавычки ожидается запятая");
                }
            }
            else {
                field.append(c);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Незакрытая кавычка в CSV");
        }

        fields.add(field.toString().trim());
        return fields;
    }

    /** Обработка сущностей из диаграммы BootcampLabs без самой базы данных. */
    private static ProcessingResult validateAndSummarize(List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV пуст");
        }

        String header = lines.get(0).replaceFirst("^\\uFEFF", "");
        List<String> columns = parseCsvRow(header);
        EntityKind kind = EntityKind.fromHeader(columns);
        int count = 0;
        long totalUnits = 0;
        BigDecimal sumOfPrices = BigDecimal.ZERO;
        Map<DeliveryStatus, Integer> statuses = new EnumMap<>(DeliveryStatus.class);
        Set<String> keys = new HashSet<>();

        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);

            if (line.isBlank()) {
                continue;
            }

            int number = lineIndex + 1;
            try {
                List<String> row = parseCsvRow(line);

                if (row.size() != columns.size()) {
                    throw new IllegalArgumentException("неверное количество столбцов");
                }

                if (row.stream().anyMatch(String::isBlank)) {
                    throw new IllegalArgumentException("обязательное поле пусто");
                }

                String key;
                switch (kind) {
                    case DARKSTORE -> {
                        key = String.valueOf(positiveInt(row.get(0), "id"));
                    }
                    case GOOD -> {
                        key = String.valueOf(positiveInt(row.get(0), "id"));
                        BigDecimal price = new BigDecimal(row.get(2));

                        if (price.signum() < 0) {
                            throw new IllegalArgumentException("цена не может быть отрицательной");
                        }

                        sumOfPrices = sumOfPrices.add(price);
                    }
                    case DELIVERY -> {
                        key = String.valueOf(positiveInt(row.get(0), "id"));

                        positiveInt(row.get(1), "id_darkstore");
                        LocalDateTime.parse(row.get(2), DELIVERY_DATE_FORMAT);
                        DeliveryStatus status = DeliveryStatus.valueOf(row.get(3));
                        statuses.merge(status, 1, Integer::sum);
                    }
                    case GOODS_LIST -> {
                        int goodId = positiveInt(row.get(0), "id_good");
                        int deliveryId = positiveInt(row.get(1), "id_delivery");
                        int quantity = positiveInt(row.get(2), "count");

                        key = goodId + ":" + deliveryId;
                        totalUnits += quantity;
                    }
                    default -> throw new IllegalStateException("Неизвестная сущность");
                }

                if (!keys.add(key)) {
                    throw new IllegalArgumentException("дублирующийся ключ " + key);
                }

                count++;
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new IllegalArgumentException("Строка " + number + ": " + e.getMessage(), e);
            }
        }

        if (count == 0) {
            throw new IllegalArgumentException("В CSV нет записей (возможно, файл ещё записывается)");
        }

        String summary = switch (kind) {
            case DARKSTORE -> "обнаружено дарксторов: " + count;
            case GOOD -> "товаров: " + count + ", сумма цен: " + sumOfPrices.toPlainString();
            case DELIVERY -> "доставок: " + count + ", статусы: " + statuses;
            case GOODS_LIST -> "позиций: " + count + ", всего единиц: " + totalUnits;
        };

        return new ProcessingResult(kind, count, summary);
    }

    private static int positiveInt(String value, String field) {
        int number = Integer.parseInt(value);

        if (number <= 0) {
            throw new IllegalArgumentException(field + " должно быть положительным");
        }

        return number;
    }

    /** Пункт 6: SHA-256 вычисляем потоково по исходным байтам до перемещения. */
    private static String calculateSha256(Path file) throws IOException {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 не поддерживается JDK", e);
        }

        try (InputStream input = Files.newInputStream(file)) {
            byte[] chunk = new byte[8192];
            int read;

            while ((read = input.read(chunk)) != -1) {
                digest.update(chunk, 0, read);
            }
        }

        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    /** Пункт 7: непрерывно отслеживаем новые / изменённые CSV через WatchService. */
    private static void watchIncomingDirectory(WatchService watcher) throws IOException, InterruptedException {
        System.out.printf("%nWatchService запущен. Ожидание новых CSV в %s%n", INCOMING);
        System.out.println("Чтобы остановить приложение, нажмите Ctrl+C.");

        while (true) {
            WatchKey key = watcher.take();
            boolean overflow = false;

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    overflow = true;
                    continue;
                }

                Path name = (Path) event.context();
                Path file = INCOMING.resolve(name);

                if (!isCsv(file)) {
                    continue;
                }

                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    System.out.println("Обнаружен новый файл: " + name);
                }

                if (waitUntilStable(file)) {
                    processFile(file, false);
                }
            }

            if (overflow) {
                System.err.println("Часть событий потеряна. Повторный поиск файлов...");
                processExistingFiles();
            }

            if (!key.reset()) {
                System.err.println("Наблюдаемый каталог стал недоступен");
                break;
            }
        }
    }

    /** Проверка неизменности размера и времени изменения файла дважды подряд. */
    private static boolean waitUntilStable(Path file) throws InterruptedException {
        int stable = 0;
        long lastSize = -1;
        java.nio.file.attribute.FileTime lastModified = null;

        for (int attempt = 0; attempt < 20; attempt++) {
            if (!Files.isRegularFile(file)) {
                return false;
            }

            try {
                long size = Files.size(file);
                var modified = Files.getLastModifiedTime(file);

                if (size > 0 && size == lastSize && modified.equals(lastModified)) {
                    stable++;

                    if (stable >= 2) {
                        return true;
                    }
                }
                else {
                    stable = 0;
                }

                lastSize = size;
                lastModified = modified;
            }
            catch (IOException ignored) {
                stable = 0;
            }

            TimeUnit.MILLISECONDS.sleep(200);
        }
        System.err.println("Файл ещё изменяется: " + file.getFileName());

        return false;
    }

    private enum DeliveryStatus {
        COMPLETED, CANCELLED, DELIVERY, ASSEMBLY, SUBMITTED
    }

    private enum EntityKind {
        DARKSTORE("darkstore", List.of("id", "address")),
        GOOD("good", List.of("id", "name", "price")),
        DELIVERY("delivery", List.of("id", "id_darkstore", "datetime_delivery", "status")),
        GOODS_LIST("goods_list", List.of("id_good", "id_delivery", "count"));

        private final String tableName;
        private final List<String> columns;

        EntityKind(String tableName, List<String> columns) {
            this.tableName = tableName;
            this.columns = columns;
        }

        private static EntityKind fromHeader(List<String> header) {
            for (EntityKind kind : values()) {
                if (kind.columns.equals(header)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Неизвестный заголовок CSV: " + header);
        }
    }

    private record ProcessingResult(EntityKind kind, int rows, String summary) {}
}
