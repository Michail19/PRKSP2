package ru.mirea.practice7.deliveries;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static ru.mirea.practice7.deliveries.ApiDtos.UpdateDelivery;
import static ru.mirea.practice7.deliveries.DomainExceptions.InvalidTransition;

class DeliveryServiceTest {
    private final DeliveryRepository repo = mock(DeliveryRepository.class);
    private final DeliveryEvents events = new DeliveryEvents();
    private final DeliveryService service = new DeliveryService(repo, events);

    @Test
    void acceptedTransitionEmitsEvent() {
        DeliveryEntity created = new DeliveryEntity(21L, 1L, 3, "CREATED", "Адрес",
                1000L, LocalDateTime.now());
        when(repo.findById(21L)).thenReturn(Mono.just(created));
        when(repo.save(any(DeliveryEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.update(21L, new UpdateDelivery("ACCEPTED", null)))
                .expectNextMatches(d -> d.status().equals("ACCEPTED"))
                .verifyComplete();
        StepVerifier.create(events.events(21L).take(1))
                .expectNextMatches(e -> e.status().equals("ACCEPTED"))
                .verifyComplete();
    }

    @Test
    void cannotSkipDirectlyToDelivered() {
        when(repo.findById(22L)).thenReturn(Mono.just(new DeliveryEntity(
                22L, 1L, 3, "CREATED", "Адрес", 1000L, LocalDateTime.now())));
        StepVerifier.create(service.update(22L, new UpdateDelivery("DELIVERED", null)))
                .expectError(InvalidTransition.class).verify();
        verify(repo, never()).save(any());
    }

    @Test
    void backpressureRequestsAreExplicit() {
        StepVerifier.create(service.backpressureNumbers(5), 0)
                .thenRequest(2).expectNext(1, 2)
                .thenRequest(2).expectNext(3, 4)
                .thenRequest(1).expectNext(5)
                .verifyComplete();
    }
}
