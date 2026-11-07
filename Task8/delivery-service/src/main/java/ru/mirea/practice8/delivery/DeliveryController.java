package ru.mirea.practice8.delivery;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private final DeliveryService service;
    private final DeliveryEvents events;
    DeliveryController(DeliveryService service,DeliveryEvents events){this.service=service;this.events=events;}
    @GetMapping public Flux<DeliveryDto> all(){return service.all();}
    @GetMapping("/by-order/{id}") public Mono<DeliveryDto> byOrder(@PathVariable long id){return service.byOrder(id);}
    @GetMapping("/{id}") public Mono<DeliveryDto> one(@PathVariable long id){return service.get(id);}
    @PutMapping("/{id}/status")
    public Mono<DeliveryDto> update(@PathVariable long id,@RequestBody Mono<ChangeStatus> body,
            @RequestHeader(value="X-Request-ID",defaultValue="-") String rid){
        return body.flatMap(b->service.change(id,b.status(),rid));
    }
    @GetMapping(value="/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DeliveryEvent>> stream(@PathVariable long id,
            @RequestHeader(value="X-Request-ID",defaultValue="-") String rid){
        Flux<ServerSentEvent<DeliveryEvent>> status=Flux.concat(
            service.get(id).map(d->DeliveryEvents.snapshot(d,rid)),events.live(id))
            .map(e->ServerSentEvent.<DeliveryEvent>builder(e)
                .id(e.at().toEpochMilli()+"-"+e.deliveryId()).event("delivery-status").build());
        Flux<ServerSentEvent<DeliveryEvent>> heartbeat=Flux.interval(Duration.ofSeconds(15))
            .map(t->ServerSentEvent.<DeliveryEvent>builder().comment("heartbeat").build());
        return Flux.merge(status,heartbeat);
    }
}
