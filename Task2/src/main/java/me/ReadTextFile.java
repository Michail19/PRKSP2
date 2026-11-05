package me;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ReadTextFile {
    private static final Path FILE_PATH = Path.of("sample.txt");


    public static void main(String[] args) {
        List<String> lines = List.of(
                "Это первая строка текста.",
                "Это вторая строка текста.",
                "Это третья строка текста."
        );

        try {
            // Записываем строки в файл
            writeFile(lines);

            System.out.println();

            // Читаем содержимое файла и выводим его в стандартный поток вывода
            readAndPrintFile();
        } catch (IOException e) {
            System.err.println("Ошибка работы с файлом: " + e.getMessage());
        }
    }


    /**
     * Записывает строки в текстовый файл.
     */
    private static void writeFile(List<String> lines) throws IOException {
        Files.write(FILE_PATH, lines);

        System.out.println("Файл успешно создан: " + FILE_PATH.toAbsolutePath());
    }


    /**
     * Читает файл и выводит его содержимое.
     */
    private static void readAndPrintFile() throws IOException {
        List<String> fileLines = Files.readAllLines(FILE_PATH);

        System.out.println("Содержимое файла:");

        for (String line : fileLines) {
            System.out.println(line);
        }
    }
}
