import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class AsyncSquareCalculator {

    private static final int THREAD_COUNT = 5;

    public static void main(String[] args) {

        ExecutorService executor =
                Executors.newFixedThreadPool(THREAD_COUNT);

        List<Request> requests = new ArrayList<>();

        try (Scanner scanner = new Scanner(System.in)) {

            System.out.println(
                    "Введите целое число."
            );

            System.out.println(
                    "Для выхода введите 'exit'."
            );

            while (true) {

                /*
                 * Перед очередным вводом проверяем,
                 * закончились ли ранее отправленные задачи.
                 */
                printCompletedResults(requests);

                System.out.print("> ");

                String input = scanner.nextLine();

                if ("exit".equalsIgnoreCase(input)) {
                    break;
                }

                try {
                    int number = Integer.parseInt(input);

                    /*
                     * Передаём вычисление рабочему потоку.
                     * submit() сразу возвращает Future.
                     */
                    Future<Integer> future =
                            executor.submit(
                                    () -> calculateSquare(number)
                            );

                    requests.add(
                            new Request(number, future)
                    );

                    System.out.println(
                            "Запрос для числа "
                                    + number
                                    + " принят."
                    );

                } catch (NumberFormatException e) {

                    System.out.println(
                            "Ошибка: необходимо ввести целое число."
                    );
                }
            }

            System.out.println(
                    "Ожидаем завершения оставшихся запросов..."
            );

            /*
             * При завершении программы всё-таки
             * получаем результаты оставшихся задач.
             */
            printRemainingResults(requests);

        } finally {
            executor.shutdown();
        }
    }

    /**
     * Имитирует длительную обработку запроса.
     */
    private static int calculateSquare(int number) {

        int delay =
                ThreadLocalRandom.current()
                        .nextInt(1, 6);

        try {

            Thread.sleep(delay * 1000L);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "Задача была прервана",
                    e
            );
        }

        return number * number;
    }

    /**
     * Выводит только уже завершённые задачи.
     *
     * Благодаря isDone() мы не вызываем get()
     * для незавершившейся задачи и поэтому
     * не блокируем ввод пользователя.
     */
    private static void printCompletedResults(
            List<Request> requests
    ) {

        Iterator<Request> iterator =
                requests.iterator();

        while (iterator.hasNext()) {

            Request request = iterator.next();

            Future<Integer> future =
                    request.future();

            if (future.isDone()) {

                try {

                    int result = future.get();

                    System.out.printf(
                            "Готово: %d² = %d%n",
                            request.number(),
                            result
                    );

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    return;

                } catch (ExecutionException e) {

                    System.out.println(
                            "Ошибка обработки числа "
                                    + request.number()
                    );
                }

                iterator.remove();
            }
        }
    }

    /**
     * При выходе ждём результаты всех
     * оставшихся запросов.
     */
    private static void printRemainingResults(
            List<Request> requests
    ) {

        for (Request request : requests) {

            try {

                int result =
                        request.future().get();

                System.out.printf(
                        "Готово: %d² = %d%n",
                        request.number(),
                        result
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                return;

            } catch (ExecutionException e) {

                System.out.println(
                        "Ошибка обработки числа "
                                + request.number()
                );
            }
        }
    }

    /**
     * Связывает исходное число
     * с Future его результата.
     */
    private record Request(
            int number,
            Future<Integer> future
    ) {
    }
}
