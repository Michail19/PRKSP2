package ru.mirea.practice8.order;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    private final OutboxPublisher publisher;
    private final SupplierClient supplier;
    OrderController(OrderService orders,OutboxPublisher publisher,SupplierClient supplier){
        this.orders=orders;this.publisher=publisher;this.supplier=supplier;
    }
    @GetMapping public List<OrderDto> list(){return orders.all();}
    @GetMapping("/{id}") public OrderDto get(@PathVariable long id){return orders.get(id);}
    @PostMapping public ResponseEntity<OrderDto> create(@RequestBody NewOrder request,
        @RequestHeader(value="X-Request-ID",required=false) String requestId){
        String rid=(requestId==null||requestId.isBlank())?java.util.UUID.randomUUID().toString():requestId;
        OrderDto order=orders.create(request,rid);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
    // Controlled demonstration endpoint: republishes the SAME eventId to prove idempotency.
    @PostMapping("/{id}/replay-event")
    public ResponseEntity<Map<String,String>> replay(@PathVariable long id){
        orders.get(id);
        String eventId=publisher.replay(id);
        return ResponseEntity.accepted().body(Map.of("eventId",eventId,"message","Original event republished"));
    }
    @GetMapping("/circuit-state") public Map<String,String> state(){return Map.of("supplierCircuit",supplier.breakerState());}
}
