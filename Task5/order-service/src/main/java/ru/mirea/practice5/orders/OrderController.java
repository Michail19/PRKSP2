package ru.mirea.practice5.orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Сервис заказов: читает из PostgreSQL те же сущности, что были в BootcampLabs. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final JdbcTemplate jdbc;
    private final String instanceId;

    public OrderController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.instanceId = Objects.toString(System.getenv("HOSTNAME"), "order-local");
    }

    @GetMapping("/instance")
    public Map<String, String> instance() {
        return Map.of("service", "order-service", "instanceId", instanceId);
    }

    @GetMapping
    public List<OrderListItem> all() {
        return jdbc.query("""
                SELECT id, darkstore_id, scheduled_at, status
                FROM delivery_orders ORDER BY id
                """,
                (rs, row) -> new OrderListItem(
                        rs.getLong("id"),
                        rs.getLong("darkstore_id"),
                        rs.getTimestamp("scheduled_at").toLocalDateTime(),
                        rs.getString("status")));
    }

    @GetMapping("/{id}")
    public OrderResponse byId(@PathVariable long id) {
        List<OrderHeader> headers = jdbc.query("""
                SELECT d.id, d.darkstore_id, d.scheduled_at, d.status, s.address
                FROM delivery_orders d
                JOIN darkstores s ON s.id = d.darkstore_id
                WHERE d.id = ?
                """,
                (rs, row) -> new OrderHeader(
                        rs.getLong("id"),
                        rs.getLong("darkstore_id"),
                        rs.getString("address"),
                        rs.getTimestamp("scheduled_at").toLocalDateTime(),
                        rs.getString("status")), id);

        if (headers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Заказ/доставка с ID=" + id + " не найдены");
        }
        OrderHeader h = headers.get(0);
        List<OrderItem> items = jdbc.query("""
                SELECT oi.good_id, g.name, g.price, oi.quantity
                FROM order_items oi
                JOIN goods g ON g.id = oi.good_id
                WHERE oi.order_id = ?
                ORDER BY oi.good_id
                """,
                (rs, row) -> {
                    BigDecimal price = rs.getBigDecimal("price");
                    int count = rs.getInt("quantity");
                    return new OrderItem(rs.getLong("good_id"), rs.getString("name"),
                            price, count, price.multiply(BigDecimal.valueOf(count)));
                }, id);
        BigDecimal total = items.stream()
                .map(OrderItem::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderResponse(h.id(), h.darkstoreId(), h.address(),
                h.scheduledAt(), h.status(), items, total, instanceId);
    }

    private record OrderHeader(long id, long darkstoreId, String address,
                               LocalDateTime scheduledAt, String status) {}

    public record OrderListItem(long id, long darkstoreId,
                                LocalDateTime scheduledAt, String status) {}

    public record OrderItem(long goodId, String name, BigDecimal price,
                            int quantity, BigDecimal total) {}

    public record OrderResponse(long id, long darkstoreId, String address,
                                LocalDateTime scheduledAt, String status,
                                List<OrderItem> items, BigDecimal total,
                                String servedBy) {}
}
