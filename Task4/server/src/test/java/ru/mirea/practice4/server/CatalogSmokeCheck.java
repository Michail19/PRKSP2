package ru.mirea.practice4.server;

import java.math.BigDecimal;

/** Независимая проверка данных, запускается и без Spring/Maven. */
public final class CatalogSmokeCheck {
    public static void main(String[] args) throws Exception {
        CsvCatalog data = CsvCatalog.fromResources();
        check(data.darkstoreCount() == 5, "darstores = 5");
        check(data.goodCount() == 10, "goods = 10");
        check(data.deliveryCount() == 20, "deliveries = 20");
        check(data.goodsListCount() == 30, "goods_list = 30");
        check(data.findGood(1).orElseThrow().name().contains("Маргарита"), "good id=1");
        check(data.findGood(999).isEmpty(), "good 999 missing");
        check(data.summary(1).orElseThrow().totalPrice()
                .compareTo(new BigDecimal("40399.28")) == 0, "delivery 1 total");
        System.out.println("CSV smoke check OK: 5 darkstores, 10 goods, 20 deliveries, 30 positions");
        System.out.println("delivery 1 = " + data.summary(1).orElseThrow().totalPrice());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
