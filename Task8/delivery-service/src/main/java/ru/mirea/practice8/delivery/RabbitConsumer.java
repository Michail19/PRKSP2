package ru.mirea.practice8.delivery;
import java.time.Duration;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
public class RabbitConsumer {
   private static final Logger log=LoggerFactory.getLogger(RabbitConsumer.class);
   private final ObjectMapper json;
   private final DatabaseClient db;
   private final TransactionalOperator transaction;
   private final DeliveryEvents events;
   public RabbitConsumer(ObjectMapper json,DatabaseClient db,ConnectionFactory factory,DeliveryEvents events){
      this.json=json;this.db=db;this.events=events;
      this.transaction=TransactionalOperator.create(new R2dbcTransactionManager(factory));
   }
   @RabbitListener(queues="delivery.order-created")
   public void consume(String message) throws Exception {
      OrderCreated e=json.readValue(message,OrderCreated.class);
      log.info("service=delivery requestId={} eventReceived=OrderCreated eventId={} orderId={}",
          e.requestId(),e.eventId(),e.orderId());
      Mono<Long> once=db.sql("INSERT INTO processed_events(event_id) VALUES (:eid) ON CONFLICT DO NOTHING RETURNING event_id")
          .bind("eid",e.eventId())
          .map((row,metadata)->row.get("event_id",UUID.class)).one()
          .flatMap(inserted->db.sql("""
                 INSERT INTO deliveries(order_id,status,total_kopecks)
                 VALUES (:orderId,'CREATED',:total)
                 ON CONFLICT (order_id) DO NOTHING RETURNING id
                 """)
              .bind("orderId",e.orderId()).bind("total",e.totalKopecks())
              .map((row,meta)->row.get("id",Long.class)).one());
      // block() ONLY on RabbitMQ listener worker so that ACK is issued after the DB transaction.
      // No HTTP/WebFlux handler blocks. Rabbit will re-deliver on DB error or failure before ACK.
      Long newId=once.as(transaction::transactional).block(Duration.ofSeconds(10));
      if(newId==null){
          log.info("service=delivery requestId={} eventId={} duplicateIgnored=true",e.requestId(),e.eventId());
      } else {
          log.info("service=delivery requestId={} deliveryCreated={} orderId={} eventId={}",
              e.requestId(),newId,e.orderId(),e.eventId());
          events.emit(new DeliveryEvent(newId,e.orderId(),"CREATED",e.requestId(),java.time.Instant.now()));
      }
   }
}
