package me;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ChecksumCalculator {
    private static final Path FILE_PATH = Path.of("sample.txt");


    public static void main(String[] args) {
        try {
            short checksum = calculateChecksum(FILE_PATH);

            System.out.printf(
                    "Контрольная сумма файла %s: 0x%04X%n",
                    FILE_PATH,
                    checksum & 0xFFFF
            );
        } catch (IOException e) {
            System.err.println("Ошибка чтения файла: " + e.getMessage());
        }
    }


    /**
     * Вычисляет контрольную сумму файла
     * с помощью побитовой операции XOR.
     */
    private static short calculateChecksum(Path filePath) throws IOException {
        try (
            FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)
        ) {
            ByteBuffer buffer = ByteBuffer.allocate(2);
            short checksum = 0;

            while (channel.read(buffer) != -1) {
                /*
                 * Переключаем ByteBuffer
                 * из режима записи в режим чтения.
                 */
                buffer.flip();

                while (buffer.hasRemaining()) {
                    byte value = buffer.get();

                    /*
                     * Выполняем XOR текущей
                     * контрольной суммы с байтом.
                     */
                    checksum ^= value;
                }

                /*
                 * Подготавливаем буфер
                 * к следующему чтению из файла.
                 */
                buffer.clear();
            }

            return checksum;
        }
    }
}
