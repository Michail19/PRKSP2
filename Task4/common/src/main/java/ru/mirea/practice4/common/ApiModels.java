package ru.mirea.practice4.common;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Общие контракты между RSocket-сервером и клиентом. */
public final class ApiModels {
    private ApiModels() { }

    public record Darkstore(int id, String address) { }
    public record Good(int id, String name, BigDecimal price) { }
    public record Delivery(int id, int darkstoreId,
                           LocalDateTime datetimeDelivery, String status) { }
    public record GoodsListItem(int goodId, int deliveryId, int count) { }

    // Request-Response
    public record GoodRequest(int id) { }
    public record DeliveryRequest(int id) { }
    public record DeliverySummary(int deliveryId, int darkstoreId,
                                  String address, String status,
                                  int lineCount, BigDecimal totalPrice) { }

    // Request-Stream
    public record GoodsStreamRequest(int limit, long delayMillis) { }

    // Fire-and-Forget: событие от фронтенда, ответ не ожидается
    public record ClientEvent(String type, String details) { }

    // Request-Channel: каждый запрос и каждый ответ — самостоятельное сообщение
    public record QuoteRequest(int requestId, int goodId, int quantity) { }
    public record QuoteReply(int requestId, int goodId, String goodName,
                             int quantity, BigDecimal total,
                             String status, String message) {
        public static QuoteReply error(QuoteRequest request, String message) {
            return new QuoteReply(request.requestId(), request.goodId(), null,
                    request.quantity(), null, "ERROR", message);
        }
    }
}
