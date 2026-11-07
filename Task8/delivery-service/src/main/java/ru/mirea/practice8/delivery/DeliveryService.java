package ru.mirea.practice8.delivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DeliveryService {
    private static final Logger log=LoggerFactory.getLogger(DeliveryService.class);
    private final DeliveryRepository repo;
    private final DeliveryEvents events;
    DeliveryService(DeliveryRepository repo,DeliveryEvents events){this.repo=repo;this.events=events;}
    public Flux<DeliveryDto> all(){return repo.all().filter(x->x.id()>0);}
    public Mono<DeliveryDto> get(long id){return repo.get(id);}
    public Mono<DeliveryDto> byOrder(long orderId){return repo.byOrder(orderId);}
    public Mono<DeliveryDto> change(long id,String next,String rid){
        if(next==null||next.isBlank())return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"status is required"));
        return repo.get(id).flatMap(previous->{
            if(!DeliveryTransitions.allowed(previous.status(),next))
                return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid transition: "+previous.status()+" -> "+next));
            return repo.updateIfStatus(id,previous.status(),next).flatMap(updated->{
                if(updated==0)return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"Concurrent status modification"));
                return repo.get(id);
            });
        }).doOnNext(d->{
            log.info("service=delivery requestId={} delivery={} status={}",rid,d.id(),d.status());
            events.emit(DeliveryEvents.snapshot(d,rid));
        });
    }
}
