package ru.mirea.practice8.order;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private static final Logger log=LoggerFactory.getLogger(OrderService.class);
    private final JdbcTemplate jdbc;
    private final SupplierClient supplier;
    private final ObjectMapper mapper;
    OrderService(JdbcTemplate jdbc,SupplierClient supplier,ObjectMapper mapper){
      this.jdbc=jdbc;this.supplier=supplier;this.mapper=mapper;
    }
    private static final org.springframework.jdbc.core.RowMapper<OrderDto> ROW=(r,i)->new OrderDto(
        r.getLong("id"),r.getLong("product_id"),r.getInt("quantity"),r.getLong("unit_price_kopecks"),
        r.getLong("total_kopecks"),r.getString("status"),r.getObject("created_at",java.time.OffsetDateTime.class));
    @Transactional
    public OrderDto create(NewOrder request,String rid){
        if(request.productId()<=0||request.quantity()<=0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"productId and quantity must be positive");
        Availability availability=supplier.check(request.productId(),request.quantity(),rid);
        if(!availability.available())throw new ResponseStatusException(HttpStatus.CONFLICT,"Not enough stock");
        long price=availability.unitPriceKopecks();
        long total;
        try {total=Math.multiplyExact(price,request.quantity());}
        catch(ArithmeticException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Total price overflow");}
        Long id=jdbc.queryForObject("""
            INSERT INTO orders(product_id,quantity,unit_price_kopecks,total_kopecks)
            VALUES (?,?,?,?) RETURNING id
            """,Long.class,request.productId(),request.quantity(),price,total);
        UUID eventId=UUID.randomUUID();
        OrderCreated event=new OrderCreated(eventId,id,request.productId(),request.quantity(),total,rid);
        String json;
        try {json=mapper.writeValueAsString(event);}catch(JsonProcessingException e){throw new IllegalStateException(e);}
        jdbc.update("INSERT INTO outbox_events(event_id,order_id,event_type,payload) VALUES (?,?,?,?)",
             eventId,id,"OrderCreated",json);
        log.info("service=order requestId={} orderCreated={} eventId={} outboxSaved=true",rid,id,eventId);
        return get(id);
    }
    public OrderDto get(long id){
        return jdbc.query("SELECT * FROM orders WHERE id=?",ROW,id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found: "+id));
    }
    public List<OrderDto> all(){return jdbc.query("SELECT * FROM orders ORDER BY id",ROW);}
}
