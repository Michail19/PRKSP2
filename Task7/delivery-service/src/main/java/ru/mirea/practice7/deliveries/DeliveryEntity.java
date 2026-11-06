package ru.mirea.practice7.deliveries;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("deliveries")
public class DeliveryEntity {
    @Id
    private Long id;
    @Column("order_id")
    private Long orderId;
    @Column("darkstore_id")
    private Integer darkstoreId;
    private String status;
    private String address;
    @Column("price_kopecks")
    private Long priceKopecks;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public DeliveryEntity() { }

    public DeliveryEntity(Long id, Long orderId, Integer darkstoreId, String status,
                          String address, Long priceKopecks, LocalDateTime updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.darkstoreId = darkstoreId;
        this.status = status;
        this.address = address;
        this.priceKopecks = priceKopecks;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getDarkstoreId() { return darkstoreId; }
    public void setDarkstoreId(Integer darkstoreId) { this.darkstoreId = darkstoreId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Long getPriceKopecks() { return priceKopecks; }
    public void setPriceKopecks(Long priceKopecks) { this.priceKopecks = priceKopecks; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
