package ru.mirea.practice8.delivery;
import java.time.OffsetDateTime;
public record DeliveryDto(long id,long orderId,String status,long totalKopecks,OffsetDateTime createdAt,OffsetDateTime updatedAt){}
