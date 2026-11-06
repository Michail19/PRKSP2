package ru.mirea.practice7.deliveries;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static ru.mirea.practice7.deliveries.ApiDtos.*;
import static ru.mirea.practice7.deliveries.DomainExceptions.*;

@Service
public class DeliveryService {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "CREATED", Set.of("ACCEPTED", "CANCELLED"),
            "ACCEPTED", Set.of("IN_TRANSIT", "CANCELLED"),
            "IN_TRANSIT", Set.of("DELIVERED"),
            // Исходные статусы из BootcampLabs остаются без изменений.
            "SUBMITTED", Set.of("ASSEMBLY", "CANCELLED"),
            "ASSEMBLY", Set.of("DELIVERY", "CANCELLED"),
            "DELIVERY", Set.of("COMPLETED")
    );

    private final DeliveryRepository repository;
    private final DeliveryEvents events;

    public DeliveryService(DeliveryRepository repository, DeliveryEvents events) {
        this.repository = repository;
        this.events = events;
    }

    public Flux<DeliveryDto> all(String status) {
        return repository.findAll()
                .filter(d -> status == null || d.getStatus().equalsIgnoreCase(status))
                .map(DeliveryDto::from);
    }

    public Mono<DeliveryDto> one(long id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFound(id)))
                .map(DeliveryDto::from);
    }

    public Mono<DeliveryDto> create(CreateDelivery request) {
        return Mono.defer(() -> {
            if (request == null || request.orderId() == null || request.orderId() <= 0 ||
                    request.darkstoreId() == null || request.darkstoreId() <= 0 ||
                    request.priceKopecks() == null || request.priceKopecks() < 0 ||
                    request.address() == null || request.address().isBlank()) {
                return Mono.error(new InvalidData("Укажите корректные orderId, darkstoreId, address и priceKopecks"));
            }
            DeliveryEntity entity = new DeliveryEntity(null, request.orderId(),
                    request.darkstoreId(), "CREATED", request.address().trim(),
                    request.priceKopecks(), LocalDateTime.now());
            return repository.save(entity)
                    .doOnNext(saved -> events.publish(new DeliveryEvent(
                            saved.getId(), "CREATED", saved.getStatus(), saved.getUpdatedAt())))
                    .map(DeliveryDto::from);
        });
    }

    public Mono<DeliveryDto> update(long id, UpdateDelivery request) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFound(id)))
                .flatMap(entity -> {
                    if (request == null || request.status() == null) {
                        return Mono.error(new InvalidData("Поле status обязательно"));
                    }
                    String next = request.status().trim().toUpperCase(java.util.Locale.ROOT);
                    if (!TRANSITIONS.getOrDefault(entity.getStatus(), Set.of()).contains(next)) {
                        return Mono.error(new InvalidTransition(entity.getStatus(), next));
                    }
                    if (request.address() != null && !request.address().isBlank()) {
                        entity.setAddress(request.address().trim());
                    }
                    entity.setStatus(next);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return repository.save(entity);
                })
                .doOnNext(saved -> events.publish(new DeliveryEvent(
                        saved.getId(), "STATUS_CHANGED", saved.getStatus(), saved.getUpdatedAt())))
                .map(DeliveryDto::from);
    }

    public Mono<Void> delete(long id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFound(id)))
                .flatMap(entity -> repository.delete(entity)
                        .doOnSuccess(unused -> {
                            events.publish(new DeliveryEvent(id, "DELETED", entity.getStatus(),
                                    LocalDateTime.now()));
                            events.complete(id);
                        }));
    }

    /** Отдельный источник для демонстрации request(n) через StepVerifier. */
    public Flux<Integer> backpressureNumbers(int count) {
        return Flux.range(1, count)
                .doOnRequest(n -> System.out.println("[BACKPRESSURE] requested=" + n))
                .limitRate(2);
    }
}
