package ru.mirea.practice7.orders;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CsvCatalogTest {
    @Test
    void readsOriginalBootcampData() throws IOException {
        CsvCatalog catalog = CsvCatalog.fromResources();
        assertEquals(5, catalog.darkstoreCount());
        assertEquals(10, catalog.goodCount());
        assertEquals(20, catalog.deliveryCount());
        assertEquals(30, catalog.goodsListCount());
        assertEquals(new BigDecimal("40399.28"), catalog.summary(1).orElseThrow().totalPrice());
        assertTrue(catalog.summary(999).isEmpty());
    }
}
