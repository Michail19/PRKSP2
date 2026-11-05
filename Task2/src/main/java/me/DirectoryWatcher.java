package me;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DirectoryWatcher {
    /**
     * Каталог, за которым наблюдает программа.
     */
    private static final Path WATCH_DIRECTORY = Path.of("Zad4_Base");

    /**
     * Последнее известное состояние каждого файла.
     */
    private static final Map<Path, FileSnapshot> snapshots = new HashMap<>();


    public static void main(String[] args) throws IOException, InterruptedException {
        /*
         * Создаём каталог, если его ещё нет.
         */
        Files.createDirectories(WATCH_DIRECTORY);

        /*
         * Запоминаем состояние уже существующих файлов.
         */
        firstObserve();

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            /*
             * Регистрируем три интересующих нас события.
             */
            WATCH_DIRECTORY.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );

            System.out.println("Наблюдение за каталогом: " + WATCH_DIRECTORY.toAbsolutePath());
            System.out.println("Для завершения программы нажмите Ctrl+C.");

            while (true) {
                /*
                 * Ожидаем появления событий.
                 * Поток здесь блокируется без активного ожидания.
                 */
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    /*
                     * OVERFLOW означает потерю части событий.
                     */
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        System.out.println("Предупреждение: потеряно " + "одно или несколько событий.");
                        continue;
                    }

                    Path relativePath = (Path) event.context();
                    Path filePath = WATCH_DIRECTORY.resolve(relativePath);

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        handleCreate(filePath);
                    }
                    else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        handleModify(filePath);
                    }
                    else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        handleDelete(filePath);
                    }
                }

                /*
                 * Повторно активируем WatchKey.
                 */
                boolean valid = key.reset();

                if (!valid) {
                    System.out.println("Каталог больше недоступен.");
                    break;
                }
            }
        }
    }


    /**
     * Первичный просмотр каталога.
     *
     * Нужен для того, чтобы программа знала
     * состояние файлов, существовавших ещё
     * до запуска WatchService.
     */
    private static void firstObserve() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(WATCH_DIRECTORY)) {
            for (Path filePath : stream) {
                if (Files.isRegularFile(filePath)) {
                    try {
                        snapshots.put(filePath, createSnapshot(filePath));
                    } catch (IOException e) {
                        System.err.println("Не удалось прочитать файл " + filePath + ": " + e.getMessage());
                    }
                }
            }
        }
    }


    /**
     * Обработка создания файла.
     */
    private static void handleCreate(Path filePath) {
        System.out.println();
        System.out.println("Создан новый файл: " + filePath.getFileName());

        try {
            if (Files.isRegularFile(filePath)) {
                snapshots.put(filePath, createSnapshot(filePath));
            }
        }
        catch (IOException e) {
            System.err.println("Не удалось сохранить состояние файла: " + e.getMessage());
        }
    }


    /**
     * Обработка изменения файла.
     */
    private static void handleModify(Path filePath) {
        try {
            if (!Files.isRegularFile(filePath)) {
                return;
            }

            FileSnapshot oldSnapshot = snapshots.get(filePath);
            FileSnapshot newSnapshot = createSnapshot(filePath);

            System.out.println();
            System.out.println("Файл изменён: " + filePath.getFileName());

            if (oldSnapshot != null) {
                printChanges(oldSnapshot.lines(), newSnapshot.lines());
            }

            /*
             * После обработки изменения сохраняем
             * новое состояние файла.
             */
            snapshots.put(filePath, newSnapshot);
        }
        catch (NoSuchFileException e) {
            /*
             * Файл мог быть удалён практически
             * одновременно с событием MODIFY.
             */
        }
        catch (IOException e) {
            System.err.println("Ошибка чтения изменённого файла: " + e.getMessage());
        }
    }


    /**
     * Обработка удаления файла.
     */
    private static void handleDelete(Path filePath) {
        System.out.println();
        System.out.println("Удалён файл: " + filePath.getFileName());

        /*
         * Сам файл уже не существует.
         * Поэтому получаем его последнее
         * сохранённое состояние.
         */
        FileSnapshot snapshot = snapshots.remove(filePath);

        if (snapshot == null) {
            System.out.println("Информация о файле отсутствует.");
            return;
        }

        System.out.println("Размер файла: " + snapshot.size() + " байт");
        System.out.printf("Контрольная сумма: 0x%04X%n", snapshot.checksum() & 0xFFFF);
    }


    /**
     * Сравнивает старое и новое содержимое.
     */
    private static void printChanges(List<String> oldLines, List<String> newLines) {
        List<String> addedLines =
            newLines.stream()
                .filter(line -> !oldLines.contains(line))
                .toList();

        List<String> deletedLines =
            oldLines.stream()
                .filter(line -> !newLines.contains(line))
                .toList();

        if (addedLines.isEmpty() && deletedLines.isEmpty()) {
            System.out.println("Добавленных или удалённых строк нет.");
            return;
        }

        if (!addedLines.isEmpty()) {
            System.out.println("Добавленные строки:");

            for (String line : addedLines) {
                System.out.println("+ " + line);
            }
        }

        if (!deletedLines.isEmpty()) {
            System.out.println("Удалённые строки:");

            for (String line : deletedLines) {
                System.out.println("- " + line);
            }
        }
    }


    /**
     * Создаёт снимок текущего состояния файла:
     * строки, размер и контрольную сумму.
     */
    private static FileSnapshot createSnapshot(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        long size = Files.size(filePath);
        short checksum = calculateChecksum(filePath);

        return new FileSnapshot(
                lines,
                size,
                checksum
        );
    }


    /**
     * Контрольная сумма из задания №3.
     *
     * Выполняется XOR над байтами файла.
     */
    private static short calculateChecksum(Path filePath) throws IOException {
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(2);
            short checksum = 0;

            while (channel.read(buffer) != -1) {
                buffer.flip();

                while (buffer.hasRemaining()) {
                    checksum ^= buffer.get();
                }

                buffer.clear();
            }

            return checksum;
        }
    }


    /**
     * Последнее известное состояние файла.
     */
    private record FileSnapshot(
            List<String> lines,
            long size,
            short checksum
    ) {
    }
}
