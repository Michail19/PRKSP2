import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class MaxElementComparison {

    private static final int ARRAY_SIZE = 10_000;
    private static final int FORK_JOIN_THRESHOLD = 1_000;

    public static void main(String[] args)
            throws InterruptedException, ExecutionException {

        List<Integer> numbers = generateNumbers();

        measure(
                "Последовательный алгоритм",
                () -> findMaxSequential(numbers)
        );

        measure(
                "ExecutorService + Future",
                () -> findMaxParallel(numbers)
        );

        measure(
                "ForkJoinPool",
                () -> findMaxForkJoin(numbers)
        );
    }

    /**
     * Создание списка из 10 000 случайных целых чисел.
     */
    private static List<Integer> generateNumbers() {
        Random random = new Random();
        List<Integer> numbers = new ArrayList<>(ARRAY_SIZE);

        for (int i = 0; i < ARRAY_SIZE; i++) {
            numbers.add(random.nextInt());
        }

        return numbers;
    }

    /**
     * Последовательный поиск максимального элемента.
     */
    private static int findMaxSequential(List<Integer> numbers)
            throws InterruptedException {

        validateList(numbers);

        int max = numbers.get(0);

        for (int number : numbers) {

            // Искусственная задержка согласно условию задания.
            Thread.sleep(1);

            if (number > max) {
                max = number;
            }
        }

        return max;
    }

    /**
     * Многопоточный поиск максимума через ExecutorService и Future.
     */
    private static int findMaxParallel(List<Integer> numbers)
            throws InterruptedException, ExecutionException {

        validateList(numbers);

        int threadCount =
                Runtime.getRuntime().availableProcessors();

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        try {
            List<Callable<Integer>> tasks = new ArrayList<>();

            int partSize = numbers.size() / threadCount;

            for (int i = 0; i < threadCount; i++) {

                int fromIndex = i * partSize;

                int toIndex = (i == threadCount - 1)
                        ? numbers.size()
                        : (i + 1) * partSize;

                tasks.add(() ->
                        findMaxInRange(numbers, fromIndex, toIndex)
                );
            }

            List<Future<Integer>> futures =
                    executor.invokeAll(tasks);

            int max = Integer.MIN_VALUE;

            for (Future<Integer> future : futures) {
                int partialMax = future.get();

                if (partialMax > max) {
                    max = partialMax;
                }
            }

            return max;

        } finally {
            executor.shutdown();
        }
    }

    /**
     * Поиск максимума в заданном диапазоне списка.
     */
    private static int findMaxInRange(
            List<Integer> numbers,
            int fromIndex,
            int toIndex
    ) throws InterruptedException {

        int max = Integer.MIN_VALUE;

        for (int i = fromIndex; i < toIndex; i++) {

            Thread.sleep(1);

            int number = numbers.get(i);

            if (number > max) {
                max = number;
            }
        }

        return max;
    }

    /**
     * Поиск максимума через ForkJoinPool.
     */
    private static int findMaxForkJoin(List<Integer> numbers) {

        validateList(numbers);

        ForkJoinPool pool = new ForkJoinPool();

        try {
            MaxFinderTask task =
                    new MaxFinderTask(
                            numbers,
                            0,
                            numbers.size()
                    );

            return pool.invoke(task);

        } finally {
            pool.shutdown();
        }
    }

    /**
     * Проверка входного списка.
     */
    private static void validateList(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Список не должен быть null или пустым"
            );
        }
    }

    /**
     * Измерение времени и приблизительного расхода памяти.
     */
    private static void measure(
            String algorithmName,
            MaxOperation operation
    ) throws InterruptedException, ExecutionException {

        Runtime runtime = Runtime.getRuntime();

        long memoryBefore =
                runtime.totalMemory() - runtime.freeMemory();

        long startTime = System.nanoTime();

        int result = operation.execute();

        long endTime = System.nanoTime();

        long memoryAfter =
                runtime.totalMemory() - runtime.freeMemory();

        double timeMs =
                (endTime - startTime) / 1_000_000.0;

        long memoryDifference =
                memoryAfter - memoryBefore;

        System.out.println();
        System.out.println(algorithmName);
        System.out.println("Максимальный элемент: " + result);

        System.out.printf(
                "Время: %.3f мс%n",
                timeMs
        );

        System.out.printf(
                "Изменение используемой памяти: %.3f МБ%n",
                memoryDifference / 1024.0 / 1024.0
        );
    }

    /**
     * Функциональный интерфейс нужен только для общего метода measure().
     */
    @FunctionalInterface
    private interface MaxOperation {

        int execute()
                throws InterruptedException, ExecutionException;
    }

    /**
     * Рекурсивная задача для ForkJoinPool.
     */
    private static class MaxFinderTask
            extends RecursiveTask<Integer> {

        private final List<Integer> numbers;
        private final int fromIndex;
        private final int toIndex;

        private MaxFinderTask(
                List<Integer> numbers,
                int fromIndex,
                int toIndex
        ) {
            this.numbers = numbers;
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
        }

        @Override
        protected Integer compute() {

            int size = toIndex - fromIndex;

            /*
             * Маленькую задачу больше не разделяем,
             * а выполняем последовательно.
             */
            if (size <= FORK_JOIN_THRESHOLD) {
                try {
                    return findMaxInRange(
                            numbers,
                            fromIndex,
                            toIndex
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }

            int middle =
                    fromIndex + size / 2;

            MaxFinderTask leftTask =
                    new MaxFinderTask(
                            numbers,
                            fromIndex,
                            middle
                    );

            MaxFinderTask rightTask =
                    new MaxFinderTask(
                            numbers,
                            middle,
                            toIndex
                    );

            leftTask.fork();

            int rightResult =
                    rightTask.compute();

            int leftResult =
                    leftTask.join();

            return Math.max(
                    leftResult,
                    rightResult
            );
        }
    }
}
