package ru.mirea.practice8.delivery;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestIdWebFilter implements WebFilter {
   private static final Logger log=LoggerFactory.getLogger(RequestIdWebFilter.class);
   @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
      String incoming=exchange.getRequest().getHeaders().getFirst("X-Request-ID");
      String id=(incoming==null||incoming.isBlank())?UUID.randomUUID().toString():incoming;
      exchange.getResponse().getHeaders().set("X-Request-ID",id);
      log.info("service=delivery requestId={} method={} path={}",id,exchange.getRequest().getMethod(),exchange.getRequest().getPath());
      var request=exchange.getRequest().mutate().headers(h->h.set("X-Request-ID",id)).build();
      return chain.filter(exchange.mutate().request(request).build());
   }
}
