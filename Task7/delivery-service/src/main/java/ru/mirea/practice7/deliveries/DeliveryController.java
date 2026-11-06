package ru.mirea.practice7.deliveries;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static ru.mirea.practice7.deliveries.ApiDtos.*;
import static ru.mirea.practice7.deliveries.DomainExceptions.*;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private final DeliveryService service;
    private final DeliveryEvents events;
    private final WebClient orders;

    public DeliveryController(DeliveryService service, DeliveryEvents events,
                              WebClient.Builder webClientBuilder,
                              @Value("${app.order-service-url}") String orderServiceUrl) {
        this.service = service;
        this.events = events;
        this.orders = webClientBuilder.baseUrl(orderServiceUrl).build();
    }

    @GetMapping
    public Flux<DeliveryDto> list(@RequestParam(required = false) String status) {
        return service.all(status);
    }

    @GetMapping("/{id}")
    public Mono<DeliveryDto> get(@PathVariable long id) {
        return service.one(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeliveryDto> create(@RequestBody Mono<CreateDelivery> input) {
        return input.flatMap(service::create);
    }

    @PutMapping("/{id}")
    public Mono<DeliveryDto> update(@PathVariable long id,
                                    @RequestBody Mono<UpdateDelivery> input) {
        return input.flatMap(body -> service.update(id, body));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable long id) {
        return service.delete(id).thenReturn(ResponseEntity.noContent().build());
    }

    /** SSE: снимок текущей доставки, затем реальные события от PUT/POST/DELETE. */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DeliveryEvent>> stream(@PathVariable long id) {
        return service.one(id).flatMapMany(current -> Flux.concat(
                Flux.just(new DeliveryEvent(id, "SNAPSHOT", current.status(), current.updatedAt())),
                events.events(id)
        )).map(e -> ServerSentEvent.<DeliveryEvent>builder(e)
                .event(e.type())
                .build());
    }

    /** Автоматический демонстрационный SSE-поток без изменения хранимого статуса. */
    @GetMapping(value = "/{id}/demo-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DeliveryEvent>> demoStream(@PathVariable long id) {
        List<String> path = List.of("CREATED", "ACCEPTED", "IN_TRANSIT", "DELIVERED");
        return service.one(id).flatMapMany(found -> Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
                .take(path.size())
                .map(i -> new DeliveryEvent(id, "DEMO", path.get(i.intValue()), LocalDateTime.now()))
                .map(e -> ServerSentEvent.<DeliveryEvent>builder(e).event("DEMO").build()));
    }

    /** WebClient: реальный HTTP-вызов к отдельному Order Service. */
    @GetMapping("/{id}/with-order")
    public Mono<EnrichedDelivery> withOrder(@PathVariable long id) {
        return service.one(id)
                .flatMap(delivery -> orders.get()
                        .uri("/api/orders/{id}", delivery.orderId())
                        .retrieve()
                        .bodyToMono(OrderSummary.class)
                        .map(order -> new EnrichedDelivery(delivery, order))
                        .onErrorResume(WebClientResponseException.NotFound.class,
                                err -> Mono.error(new InvalidData(
                                        "Order Service: заказ " + delivery.orderId() + " отсутствует")))
                        .onErrorResume(WebClientRequestException.class,
                                err -> Mono.error(new RemoteServiceUnavailable(
                                        "Order Service недоступен: " + err.getMessage()))));
    }

    @GetMapping(value = "/backpressure-demo", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Integer>> backpressureDemo(@RequestParam(defaultValue = "10") int count) {
        if (count < 1 || count > 100) {
            return Flux.error(new InvalidData("count должен быть от 1 до 100"));
        }
        return service.backpressureNumbers(count)
                .delayElements(Duration.ofMillis(150))
                .map(value -> ServerSentEvent.<Integer>builder(value).event("NUMBER").build());
    }
}
