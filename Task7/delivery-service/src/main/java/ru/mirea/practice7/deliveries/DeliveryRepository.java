package ru.mirea.practice7.deliveries;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface DeliveryRepository extends ReactiveCrudRepository<DeliveryEntity, Long> { }
