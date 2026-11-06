package ru.mirea.practice7.deliveries;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class DeliveryHttpTest {
    @Autowired WebTestClient client;

    @Test
    void readsHistoricalDelivery() {
        client.get().uri("/api/deliveries/1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.priceKopecks").isEqualTo(4039928);
    }

    @Test
    void unknownDeliveryReturns404() {
        client.get().uri("/api/deliveries/99999").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void canCreateNewDelivery() {
        client.post().uri("/api/deliveries").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiDtos.CreateDelivery(1L, 3, "Новый адрес", 4039928L))
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("CREATED")
                .jsonPath("$.id").exists();
    }
}
