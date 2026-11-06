package ru.mirea.practice7.deliveries;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.util.concurrent.ConcurrentHashMap;

import static ru.mirea.practice7.deliveries.ApiDtos.DeliveryEvent;

/** Горячий поток событий. Для учебной демонстрации хранится последнее событие на доставку. */
@Component
public class DeliveryEvents {
    private final ConcurrentHashMap<Long, Sinks.Many<DeliveryEvent>> streams =
            new ConcurrentHashMap<>();

    private Sinks.Many<DeliveryEvent> sink(long id) {
        return streams.computeIfAbsent(id, ignored -> Sinks.many().replay().limit(1));
    }

    public Flux<DeliveryEvent> events(long id) {
        return sink(id).asFlux();
    }

    public void publish(DeliveryEvent event) {
        Sinks.EmitResult result = sink(event.deliveryId()).tryEmitNext(event);
        if (result.isFailure()) {
            throw new IllegalStateException("Не удалось отправить событие: " + result);
        }
    }

    public void complete(long id) {
        sink(id).tryEmitComplete();
    }
}
