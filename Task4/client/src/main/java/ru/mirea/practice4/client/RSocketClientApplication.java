package ru.mirea.practice4.client;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.rsocket.RSocketRequester;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static ru.mirea.practice4.common.ApiModels.*;

/** Клиент показывает request-response, request-stream, fire-and-forget и channel. */
@SpringBootApplication
public class RSocketClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(RSocketClientApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(RSocketRequester.Builder builder) {
        return args -> {
            String host = System.getProperty("rsocket.host", "localhost");
            int port = Integer.getInteger("rsocket.port", 7000);
            RSocketRequester requester;
            try {
                requester = builder.tcp(host, port);
            } catch (Exception ex) {
                System.err.println("[CLIENT][ERROR] Не удалось подключиться к RSocket "
                        + host + ":" + port + ". Сначала запустите server.");
                return;
            }
            try {
                requestResponseDemo(requester);
                requestStreamDemo(requester);
                fireAndForgetDemo(requester);
                requestChannelDemo(requester);
                System.out.println("[CLIENT] Все демонстрации завершены.");
            } finally {
                requester.dispose();
            }
        };
    }

    private static void requestResponseDemo(RSocketRequester requester) {
        System.out.println("\n=== 1. REQUEST-RESPONSE ===");
        requester.route("goods.get")
                .data(new GoodRequest(1))
                .retrieveMono(Good.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(good -> System.out.printf(Locale.ROOT,
                        "[CLIENT][RESPONSE] Товар #%d: %s, цена=%s руб.%n",
                        good.id(), good.name(), good.price()))
                .onErrorResume(ex -> logError("goods.get(1)", ex))
                .block();

        // Обработка несуществующего объекта: клиент не падает.
        requester.route("goods.get")
                .data(new GoodRequest(999))
                .retrieveMono(Good.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(good -> System.out.println("[CLIENT] " + good))
                .onErrorResume(ex -> logError("goods.get(999)", ex))
                .block();

        requester.route("deliveries.summary")
                .data(new DeliveryRequest(1))
                .retrieveMono(DeliverySummary.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(summary -> System.out.printf(Locale.ROOT,
                        "[CLIENT][DELIVERY] #%d, darkstore=%d, status=%s, позиций=%d, сумма=%s руб.%n",
                        summary.deliveryId(), summary.darkstoreId(), summary.status(),
                        summary.lineCount(), summary.totalPrice()))
                .onErrorResume(ex -> logError("deliveries.summary(1)", ex))
                .block();
    }

    private static void requestStreamDemo(RSocketRequester requester) {
        System.out.println("\n=== 2. REQUEST-STREAM: 5 товаров с задержкой 350 мс ===");
        long start = System.nanoTime();
        AtomicInteger received = new AtomicInteger();
        requester.route("goods.stream")
                .data(new GoodsStreamRequest(5, 350))
                .retrieveFlux(Good.class)
                .timeout(Duration.ofSeconds(8))
                // Reactor: событие видно сразу по его поступлении, а не после сбора списка.
                .doOnNext(good -> System.out.printf(Locale.ROOT,
                        "[CLIENT][STREAM] +%4d мс | #%d %s: %s руб. | поток=%s%n",
                        (System.nanoTime() - start) / 1_000_000,
                        good.id(), good.name(), good.price(),
                        Thread.currentThread().getName()))
                // Клиентская реактивная обработка: filter и map.
                .filter(good -> good.price().compareTo(new BigDecimal("200.00")) >= 0)
                .map(good -> "Карточка товара: " + good.name() + " (" + good.price() + " руб.)")
                .doOnNext(card -> {
                    received.incrementAndGet();
                    System.out.println("[CLIENT][REACTOR] " + card);
                })
                .onErrorResume(ex -> {
                    System.err.println("[CLIENT][ERROR] stream: " + ex.getMessage());
                    return Flux.empty();
                })
                .blockLast(Duration.ofSeconds(12));
        System.out.println("[CLIENT] Карточек после filter: " + received.get());
    }

    private static void fireAndForgetDemo(RSocketRequester requester) {
        System.out.println("\n=== 3. FIRE-AND-FORGET ===");
        requester.route("events.report")
                .data(new ClientEvent("GOOD_VIEW", "Пользователь просмотрел товар goodId=1"))
                .send()
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess(ignored -> System.out.println(
                        "[CLIENT][FNF] Событие отправлено; бизнес-ответ не ожидается. "
                                + "Проверить получение можно в консоли сервера."))
                .onErrorResume(ex -> {
                    System.err.println("[CLIENT][ERROR] FNF: " + ex.getMessage());
                    return Mono.empty();
                })
                .block();
    }

    private static void requestChannelDemo(RSocketRequester requester) {
        System.out.println("\n=== 4. REQUEST-CHANNEL ===");
        Flux<QuoteRequest> outgoing = Flux.fromIterable(List.of(
                        new QuoteRequest(1, 1, 2),
                        new QuoteRequest(2, 2, 3),
                        new QuoteRequest(3, 999, 1), // несуществующий товар
                        new QuoteRequest(4, 4, 0),   // некорректное количество
                        new QuoteRequest(5, 6, 4)))
                .delayElements(Duration.ofMillis(180))
                .doOnNext(q -> System.out.printf(
                        "[CLIENT][CHANNEL ->] requestId=%d goodId=%d count=%d%n",
                        q.requestId(), q.goodId(), q.quantity()))
                .subscribeOn(Schedulers.boundedElastic());

        requester.route("goods.quote.channel")
                .data(outgoing, QuoteRequest.class)
                .retrieveFlux(QuoteReply.class)
                .timeout(Duration.ofSeconds(8))
                .doOnNext(reply -> System.out.printf(Locale.ROOT,
                        "[CLIENT][CHANNEL <-] requestId=%d status=%s item=%s cost=%s note=%s%n",
                        reply.requestId(), reply.status(), reply.goodName(),
                        reply.total(), reply.message()))
                .onErrorResume(ex -> {
                    System.err.println("[CLIENT][ERROR] channel: " + ex.getMessage());
                    return Flux.empty();
                })
                .blockLast(Duration.ofSeconds(15));
    }

    private static <T> Mono<T> logError(String operation, Throwable ex) {
        System.err.printf("[CLIENT][ERROR] %s: %s%n", operation, ex.getMessage());
        return Mono.empty();
    }
}
