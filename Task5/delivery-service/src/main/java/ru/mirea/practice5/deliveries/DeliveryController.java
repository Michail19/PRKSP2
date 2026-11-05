package ru.mirea.practice5.deliveries;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Configuration
class OrderClientConfiguration {
    @Bean
    RestClient orderRestClient(RestClient.Builder builder,
            @Value("${clients.order-service.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}

/** Сервис доставок не читает БД напрямую: обращается к Order Service через HTTP. */
@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private final RestClient orderRestClient;
    private final String instanceId;

    public DeliveryController(RestClient orderRestClient) {
        this.orderRestClient = orderRestClient;
        this.instanceId = Objects.toString(System.getenv("HOSTNAME"), "delivery-local");
    }

    @GetMapping("/instance")
    public Map<String, String> instance() {
        return Map.of("service", "delivery-service", "instanceId", instanceId,
                "time", Instant.now().toString());
    }

    @GetMapping("/{id}")
    public DeliveryView byId(@PathVariable long id) {
        try {
            OrderResponse order = orderRestClient.get()
                    .uri("/api/orders/{id}", id)
                    .retrieve()
                    .body(OrderResponse.class);

            if (order == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Order Service вернул пустой ответ");
            }
            return new DeliveryView(order.id(), order.status(),
                    order.darkstoreId(), order.address(), order.scheduledAt(),
                    order.items(), order.total(), instanceId, order.servedBy());

        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Доставка с ID=" + id + " не найдена");
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Order Service вернул ошибку HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Order Service временно недоступен");
        }
    }

    public record OrderItem(long goodId, String name, BigDecimal price,
                            int quantity, BigDecimal total) {}

    // DTO, совпадающий с JSON контрактом Order Service, без прямой Java-зависимости.
    public record OrderResponse(long id, long darkstoreId, String address,
                                LocalDateTime scheduledAt, String status,
                                List<OrderItem> items, BigDecimal total,
                                String servedBy) {}

    public record DeliveryView(long deliveryId, String status, long darkstoreId,
                               String address, LocalDateTime scheduledAt,
                               List<OrderItem> items, BigDecimal total,
                               String servedBy, String orderServiceInstance) {}
}
