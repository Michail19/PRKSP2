package ru.mirea.practice7.deliveries;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ApiDtos {
    private ApiDtos() { }

    public record DeliveryDto(Long id, Long orderId, Integer darkstoreId, String status,
                              String address, Long priceKopecks, LocalDateTime updatedAt) {
        public static DeliveryDto from(DeliveryEntity e) {
            return new DeliveryDto(e.getId(), e.getOrderId(), e.getDarkstoreId(),
                    e.getStatus(), e.getAddress(), e.getPriceKopecks(), e.getUpdatedAt());
        }
    }
    public record CreateDelivery(Long orderId, Integer darkstoreId, String address,
                                 Long priceKopecks) { }
    public record UpdateDelivery(String status, String address) { }
    public record DeliveryEvent(Long deliveryId, String type, String status, LocalDateTime time) { }
    public record OrderSummary(Integer deliveryId, Integer darkstoreId, String address,
                               String status, Integer lineCount, BigDecimal totalPrice) { }
    public record EnrichedDelivery(DeliveryDto delivery, OrderSummary order) { }
    public record ApiError(String message, String code) { }
}
