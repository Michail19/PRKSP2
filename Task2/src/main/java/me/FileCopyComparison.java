package me;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileCopyComparison {
    private static final int FILE_SIZE_MB = 100;
    private static final Path SOURCE = Path.of("source.bin");
    private static final Path COPY_STREAMS = Path.of("copy_streams.bin");
    private static final Path COPY_CHANNEL = Path.of("copy_channel.bin");
    private static final Path COPY_COMMONS = Path.of("copy_commons.bin");
    private static final Path COPY_FILES = Path.of("copy_files.bin");


    public static void main(String[] args) throws IOException {
        /*
         * Создаём исходный файл только если его ещё нет
         * или его размер отличается от ожидаемого.
         */
        createLargeFile(SOURCE, FILE_SIZE_MB);

        System.out.printf(
                "Исходный файл: %s%n",
                SOURCE.toAbsolutePath()
        );

        System.out.printf(
                "Размер файла: %.2f МБ%n%n",
                Files.size(SOURCE) / 1024.0 / 1024.0
        );

        measure(
                "FileInputStream / FileOutputStream",
                () -> copyUsingStreams(
                        SOURCE,
                        COPY_STREAMS
                )
        );

        measure(
                "FileChannel",
                () -> copyUsingFileChannel(
                        SOURCE,
                        COPY_CHANNEL
                )
        );

        measure(
                "Apache Commons IO",
                () -> copyUsingCommonsIO(
                        SOURCE,
                        COPY_COMMONS
                )
        );

        measure(
                "Files.copy",
                () -> copyUsingFiles(
                        SOURCE,
                        COPY_FILES
                )
        );
    }


    /**
     * Создаёт файл заданного размера.
     */
    private static void createLargeFile(Path path,  int sizeMb) throws IOException {
        long expectedSize = sizeMb * 1024L * 1024L;

        /*
         * Если файл уже создан правильно,
         * не создаём его каждый запуск заново.
         */
        if (Files.exists(path) && Files.size(path) == expectedSize) {
            return;
        }

        byte[] oneMegabyte = new byte[1024 * 1024];

        try (FileOutputStream output = new FileOutputStream(path.toFile())) {
            for (int i = 0; i < sizeMb; i++) {
                output.write(oneMegabyte);
            }
        }
    }


    /**
     * Способ 1: FileInputStream + FileOutputStream
     */
    private static void copyUsingStreams(Path source, Path destination) throws IOException {
        byte[] buffer = new byte[8192];

        try (
            FileInputStream input =new FileInputStream(source.toFile());
            FileOutputStream output = new FileOutputStream(destination.toFile())
        ) {
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }


    /**
     * Способ 2: FileChannel
     */
    private static void copyUsingFileChannel(Path source, Path destination) throws IOException {
        try (
            FileChannel sourceChannel = FileChannel.open(source);
            FileChannel destinationChannel =
                FileChannel.open(
                        destination,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.WRITE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
                )
        ) {
            long position = 0;
            long size = sourceChannel.size();

            while (position < size) {
                position +=
                    sourceChannel.transferTo(
                            position,
                            size - position,
                            destinationChannel
                    );
            }
        }
    }


    /**
     * Способ №3: Apache Commons IO
     */
    private static void copyUsingCommonsIO(Path source, Path destination) throws IOException {
        File sourceFile = source.toFile();
        File destinationFile = destination.toFile();

        FileUtils.copyFile(
                sourceFile,
                destinationFile
        );
    }


    /**
     * Способ №4: стандартный класс Files
     */
    private static void copyUsingFiles(Path source, Path destination) throws IOException {
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }


    /**
     * Запускает способ копирования
     * и приблизительно измеряет его
     * время и расход heap-памяти JVM.
     */
    private static void measure(String methodName, CopyOperation operation) throws IOException {
        Runtime runtime = Runtime.getRuntime();

        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.nanoTime();

        operation.execute();

        long endTime = System.nanoTime();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        double elapsedMs = (endTime - startTime) / 1_000_000.0;

        long memoryDifference = memoryAfter - memoryBefore;

        System.out.println("Метод: " + methodName);
        System.out.printf("Время: %.3f мс%n", elapsedMs);
        System.out.printf(
                "Изменение памяти JVM: %.3f МБ%n",
                memoryDifference
                        / 1024.0
                        / 1024.0
        );
        System.out.println();
    }


    @FunctionalInterface
    private interface CopyOperation {
        void execute()
                throws IOException;
    }
}
