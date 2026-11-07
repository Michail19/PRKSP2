package ru.mirea.practice8.delivery;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;

@Repository
public class DeliveryRepository {
    private final DatabaseClient db;
    DeliveryRepository(DatabaseClient db){this.db=db;}
    private static DeliveryDto from(io.r2dbc.spi.Row row){
        return new DeliveryDto(row.get("id",Long.class),row.get("order_id",Long.class),
            row.get("status",String.class),row.get("total_kopecks",Long.class),
            row.get("created_at",OffsetDateTime.class),row.get("updated_at",OffsetDateTime.class));
    }
    public Flux<DeliveryDto> all(){
        return db.sql("SELECT * FROM deliveries ORDER BY id")
            .map((row,meta)->from(row)).all();
    }
    public Mono<DeliveryDto> get(long id){
        return db.sql("SELECT * FROM deliveries WHERE id=:id").bind("id",id)
            .map((row,meta)->from(row)).one()
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,"Delivery not found")));
    }
    public Mono<DeliveryDto> byOrder(long orderId){
        return db.sql("SELECT * FROM deliveries WHERE order_id=:id").bind("id",orderId)
            .map((row,meta)->from(row)).one()
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,"Delivery for order not created yet")));
    }
    public Mono<Long> updateIfStatus(long id,String expected,String next){
        return db.sql("UPDATE deliveries SET status=:next,updated_at=now() WHERE id=:id AND status=:expected")
            .bind("id",id).bind("expected",expected).bind("next",next)
            .fetch().rowsUpdated();
    }
}
