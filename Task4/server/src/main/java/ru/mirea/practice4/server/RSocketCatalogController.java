package ru.mirea.practice4.server;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;

import static ru.mirea.practice4.common.ApiModels.*;

/** Все четыре модели взаимодействия RSocket + дополнительный запрос доставки. */
@Controller
public class RSocketCatalogController {
    private final CsvCatalog catalog;

    public RSocketCatalogController(CsvCatalog catalog) {
        this.catalog = catalog;
    }

    // 1. Request-Response: одно сообщение запроса -> один ответ.
    @MessageMapping("goods.get")
    public Mono<Good> getGood(@Payload GoodRequest request) {
        return Mono.defer(() -> {
            System.out.println("[SERVER] REQUEST_RESPONSE goods.get id=" + request.id());
            return catalog.findGood(request.id())
                    .map(Mono::just)
                    .orElseGet(() -> Mono.error(new IllegalArgumentException(
                            "Товар с ID=" + request.id() + " не найден")));
        });
    }

    // Дополнение: показывает связь delivery + darkstore + goods_list + good.
    @MessageMapping("deliveries.summary")
    public Mono<DeliverySummary> getDeliverySummary(@Payload DeliveryRequest request) {
        return Mono.defer(() -> catalog.summary(request.id())
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new IllegalArgumentException(
                        "Доставка с ID=" + request.id() + " не найдена"))));
    }

    // 2. Request-Stream: один запрос -> поток объектов с паузами.
    @MessageMapping("goods.stream")
    public Flux<Good> streamGoods(@Payload GoodsStreamRequest request) {
        if (request.limit() < 1 || request.limit() > 10
                || request.delayMillis() < 100 || request.delayMillis() > 2000) {
            return Flux.error(new IllegalArgumentException(
                    "limit должен быть 1..10, delayMillis — 100..2000"));
        }
        System.out.printf("[SERVER] REQUEST_STREAM limit=%d, задержка=%d мс%n",
                request.limit(), request.delayMillis());
        return Flux.fromIterable(catalog.allGoods())
                .take(request.limit())
                .delayElements(Duration.ofMillis(request.delayMillis()))
                .doOnNext(good -> System.out.printf(
                        "[SERVER] STREAM -> Товар #%d %s %.2f%n",
                        good.id(), good.name(), good.price()));
    }

    // 3. Fire-and-Forget: клиент отправляет событие; сервер не отвечает объектом.
    @MessageMapping("events.report")
    public void reportEvent(@Payload ClientEvent event) {
        System.out.printf("[SERVER] FIRE_AND_FORGET: тип=%s, сведения=%s%n",
                event.type(), event.details());
    }

    // 4. Channel: поток запросов от клиента + поток ответов от сервера.
    @MessageMapping("goods.quote.channel")
    public Flux<QuoteReply> quoteChannel(@Payload Flux<QuoteRequest> requests) {
        return requests.concatMap(request -> Mono.defer(() -> {
                    System.out.printf("[SERVER] CHANNEL <- запрос=%d goodId=%d count=%d%n",
                            request.requestId(), request.goodId(), request.quantity());
                    if (request.quantity() <= 0) {
                        return Mono.just(QuoteReply.error(request,
                                "Количество должно быть положительным"));
                    }
                    return catalog.findGood(request.goodId())
                            .<Mono<QuoteReply>>map(good -> Mono.just(new QuoteReply(
                                    request.requestId(), good.id(), good.name(),
                                    request.quantity(),
                                    good.price().multiply(BigDecimal.valueOf(request.quantity())),
                                    "OK", "Стоимость рассчитана")))
                            .orElseGet(() -> Mono.just(QuoteReply.error(request,
                                    "Товар с ID=" + request.goodId() + " не найден")));
                })
                .delayElement(Duration.ofMillis(220))
                .doOnNext(reply -> System.out.printf(
                        "[SERVER] CHANNEL -> запрос=%d status=%s%n",
                        reply.requestId(), reply.status())));
    }
}
