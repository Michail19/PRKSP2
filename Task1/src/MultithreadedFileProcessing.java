import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

public class MultithreadedFileProcessing {

    private static final int QUEUE_CAPACITY = 5;

    public static void main(String[] args) {

        FileQueue queue =
                new FileQueue(QUEUE_CAPACITY);

        Thread generatorThread =
                new Thread(
                        new FileGenerator(queue),
                        "FileGenerator"
                );

        Thread jsonProcessorThread =
                new Thread(
                        new FileProcessor(
                                queue,
                                FileType.JSON
                        ),
                        "JSON-Processor"
                );

        Thread xmlProcessorThread =
                new Thread(
                        new FileProcessor(
                                queue,
                                FileType.XML
                        ),
                        "XML-Processor"
                );

        Thread xlsProcessorThread =
                new Thread(
                        new FileProcessor(
                                queue,
                                FileType.XLS
                        ),
                        "XLS-Processor"
                );

        generatorThread.start();

        jsonProcessorThread.start();
        xmlProcessorThread.start();
        xlsProcessorThread.start();
    }


    /**
     * Возможные типы генерируемых файлов.
     */
    private enum FileType {
        XML,
        JSON,
        XLS
    }


    /**
     * Модель файла.
     *
     * Это не реальный файл на диске,
     * а объект с типом и условным размером.
     */
    private record GeneratedFile(
            FileType type,
            int size
    ) {
    }


    /**
     * Потокобезопасная очередь файлов.
     *
     * Каждый тип имеет собственную BlockingQueue,
     * а Semaphore ограничивает общее количество
     * находящихся в системе очередей файлов.
     */
    private static class FileQueue {

        private final Map<FileType, BlockingQueue<GeneratedFile>>
                queues;

        private final Semaphore freeSlots;


        public FileQueue(int capacity) {

            freeSlots = new Semaphore(capacity);

            queues = new EnumMap<>(FileType.class);

            for (FileType type : FileType.values()) {

                queues.put(
                        type,
                        new LinkedBlockingQueue<>()
                );
            }
        }


        /**
         * Добавление файла.
         *
         * Если всего уже находится 5 файлов,
         * генератор ждёт появления свободного места.
         */
        public void put(GeneratedFile file)
                throws InterruptedException {

            freeSlots.acquire();

            try {

                queues.get(file.type())
                        .put(file);

            } catch (InterruptedException e) {

                freeSlots.release();
                throw e;
            }
        }


        /**
         * Получение файла определённого типа.
         *
         * Если такого файла пока нет,
         * обработчик находится в состоянии ожидания.
         */
        public GeneratedFile take(FileType type)
                throws InterruptedException {

            GeneratedFile file =
                    queues.get(type).take();

            freeSlots.release();

            return file;
        }
    }


    /**
     * Генератор файлов.
     */
    private static class FileGenerator
            implements Runnable {

        private final FileQueue queue;


        public FileGenerator(FileQueue queue) {
            this.queue = queue;
        }


        @Override
        public void run() {

            while (!Thread.currentThread().isInterrupted()) {

                try {

                    /*
                     * Задержка генерации:
                     * от 100 до 1000 мс.
                     */
                    int generationDelay =
                            ThreadLocalRandom.current()
                                    .nextInt(100, 1001);

                    Thread.sleep(generationDelay);


                    /*
                     * Выбираем случайный тип.
                     */
                    FileType[] types =
                            FileType.values();

                    FileType type =
                            types[
                                    ThreadLocalRandom.current()
                                            .nextInt(types.length)
                                    ];


                    /*
                     * Размер от 10 до 100.
                     */
                    int size =
                            ThreadLocalRandom.current()
                                    .nextInt(10, 101);


                    GeneratedFile file =
                            new GeneratedFile(
                                    type,
                                    size
                            );


                    /*
                     * Помещаем файл в очередь.
                     */
                    queue.put(file);


                    System.out.printf(
                            "Создан файл: %-4s | размер: %d%n",
                            type,
                            size
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                }
            }
        }
    }


    /**
     * Обработчик файлов одного определённого типа.
     */
    private static class FileProcessor
            implements Runnable {

        private final FileQueue queue;
        private final FileType allowedType;


        public FileProcessor(
                FileQueue queue,
                FileType allowedType
        ) {
            this.queue = queue;
            this.allowedType = allowedType;
        }


        @Override
        public void run() {

            while (!Thread.currentThread().isInterrupted()) {

                try {

                    /*
                     * Получаем только файл того типа,
                     * который умеет обрабатывать
                     * данный обработчик.
                     */
                    GeneratedFile file =
                            queue.take(allowedType);


                    long processingTime =
                            file.size() * 7L;


                    System.out.printf(
                            "%s начал обработку: размер %d%n",
                            allowedType,
                            file.size()
                    );


                    Thread.sleep(processingTime);


                    System.out.printf(
                            "%s обработан | размер: %d"
                                    + " | время: %d мс%n",
                            file.type(),
                            file.size(),
                            processingTime
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
