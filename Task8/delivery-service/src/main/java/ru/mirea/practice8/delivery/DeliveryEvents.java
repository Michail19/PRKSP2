package ru.mirea.practice8.delivery;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class DeliveryEvents {
    private final ConcurrentHashMap<Long,Sinks.Many<DeliveryEvent>> streams=new ConcurrentHashMap<>();
    private Sinks.Many<DeliveryEvent> sink(long id){
        return streams.computeIfAbsent(id,ignored->Sinks.many().multicast().directBestEffort());
    }
    public void emit(DeliveryEvent event){
        sink(event.deliveryId()).tryEmitNext(event); // no subscribers -> snapshot in /events remains available
    }
    public Flux<DeliveryEvent> live(long id){return sink(id).asFlux();}
    public static DeliveryEvent snapshot(DeliveryDto d,String requestId){
        return new DeliveryEvent(d.id(),d.orderId(),d.status(),requestId,Instant.now());
    }
}
