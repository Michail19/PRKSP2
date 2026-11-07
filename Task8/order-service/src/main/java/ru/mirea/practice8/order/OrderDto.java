package ru.mirea.practice8.order;
import java.time.OffsetDateTime;
public record OrderDto(long id,long productId,int quantity,long unitPriceKopecks,long totalKopecks,String status,OffsetDateTime createdAt){}
