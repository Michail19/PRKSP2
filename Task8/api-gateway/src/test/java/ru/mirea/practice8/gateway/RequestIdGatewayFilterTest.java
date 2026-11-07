package ru.mirea.practice8.gateway;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class RequestIdGatewayFilterTest {
 @Test void forwardsProvidedRequestId(){
   var incoming=MockServerHttpRequest.get("/api/orders").header("X-Request-ID","exam-789");
   var exchange=MockServerWebExchange.from(incoming);
   AtomicReference<String> forwarded=new AtomicReference<>();
   new RequestIdGatewayFilter().filter(exchange,e->{
       forwarded.set(e.getRequest().getHeaders().getFirst("X-Request-ID"));
       return Mono.empty();
   }).block();
   assertEquals("exam-789",forwarded.get());
   assertEquals("exam-789",exchange.getResponse().getHeaders().getFirst("X-Request-ID"));
 }
}
