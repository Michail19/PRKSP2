package ru.mirea.practice8.delivery;
import java.time.Instant;
public record DeliveryEvent(long deliveryId,long orderId,String status,String requestId,Instant at){}
