package ru.mirea.practice7.orders;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static ru.mirea.practice7.orders.ApiModels.DeliverySummary;

/** Небольшой отдельный HTTP-сервис: заказ соответствует исторической доставке из BootcampLabs. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final CsvCatalog catalog;

    public OrderController() throws IOException {
        this.catalog = CsvCatalog.fromResources();
    }

    @GetMapping("/{id}")
    public Mono<DeliverySummary> get(@PathVariable int id) {
        return Mono.justOrEmpty(catalog.summary(id))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Заказ " + id + " не найден")));
    }
}
