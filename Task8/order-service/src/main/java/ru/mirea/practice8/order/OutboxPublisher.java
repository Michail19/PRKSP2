package ru.mirea.practice8.order;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OutboxPublisher {
    private static final Logger log=LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    public OutboxPublisher(JdbcTemplate jdbc,RabbitTemplate rabbit){this.jdbc=jdbc;this.rabbit=rabbit;}
    @Scheduled(fixedDelayString="${app.outbox.interval-ms:1000}",initialDelay=2500)
    public void publishPending(){
        List<Map<String,Object>> events=jdbc.queryForList(
          "SELECT event_id,payload FROM outbox_events WHERE published=false ORDER BY created_at LIMIT 20");
        for(var event:events){
           try {
              String id=event.get("event_id").toString();
              send((String)event.get("payload"));
              jdbc.update("UPDATE outbox_events SET published=true,published_at=now() WHERE event_id=?::uuid",id);
              log.info("service=order eventPublished=OrderCreated eventId={}",id);
           }catch(Exception ex){log.warn("Outbox publish postponed: {}",ex.toString());break;}
        }
    }
    public String replay(long orderId){
        var events=jdbc.queryForList("SELECT event_id,payload FROM outbox_events WHERE order_id=? ORDER BY created_at LIMIT 1",orderId);
        if(events.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Outbox event not found");
        var event=events.get(0);
        send((String)event.get("payload"));
        log.info("service=order replay eventId={} orderId={}",event.get("event_id"),orderId);
        return event.get("event_id").toString();
    }
    private void send(String payload){
        rabbit.invoke(ops->{
            ops.convertAndSend(RabbitTopology.EXCHANGE,RabbitTopology.KEY,payload);
            ops.waitForConfirmsOrDie(3000);
            return null;
        });
    }
}
