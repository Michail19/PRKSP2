package ru.mirea.practice8.delivery;
import java.util.UUID;
public record OrderCreated(UUID eventId,long orderId,long productId,int quantity,long totalKopecks,String requestId){}
