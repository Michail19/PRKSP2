package ru.mirea.practice3;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Сущности соответствуют четырём таблицам BootcampLabs. */
final class Domain {
    private Domain() { }

    record Darkstore(int id, String address) { }
    record Good(int id, String name, BigDecimal price) { }
    record Delivery(int id, int darkstoreId, LocalDateTime dateTime, String status) { }
    record GoodsListItem(int goodId, int deliveryId, int count) { }

    /** Вычисленная позиция доставки, готовая к агрегации. */
    record PricedItem(int darkstoreId, String address, int deliveryId,
                      String status, String goodName, int count,
                      BigDecimal price, BigDecimal total) { }

    record DarkstoreTotal(int darkstoreId, String address, BigDecimal total) { }

    record DataSet(Map<Integer, Darkstore> darkstores,
                   Map<Integer, Good> goods,
                   Map<Integer, Delivery> deliveries,
                   List<GoodsListItem> items) { }
}
