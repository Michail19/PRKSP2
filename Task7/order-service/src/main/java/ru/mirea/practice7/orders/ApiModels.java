package ru.mirea.practice7.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Модели реальных таблиц BootcampLabs и REST-ответ исторического заказа. */
public final class ApiModels {
    private ApiModels() { }

    public record Darkstore(int id, String address) { }
    public record Good(int id, String name, BigDecimal price) { }
    public record Delivery(int id, int darkstoreId,
                           LocalDateTime datetimeDelivery, String status) { }
    public record GoodsListItem(int goodId, int deliveryId, int count) { }

    /** Данные заказа, вычисленные по исходным CSV таблицам. */
    public record DeliverySummary(int deliveryId, int darkstoreId,
                                  String address, String status,
                                  int lineCount, BigDecimal totalPrice) { }
}
