package ru.mirea.practice8.gateway;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RequestIdGatewayFilter implements GlobalFilter, Ordered {
    private static final Logger log=LoggerFactory.getLogger(RequestIdGatewayFilter.class);
    @Override public int getOrder(){return -100;}
    @Override public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange,
                                        org.springframework.cloud.gateway.filter.GatewayFilterChain chain){
        String incoming=exchange.getRequest().getHeaders().getFirst("X-Request-ID");
        String id=(incoming==null||incoming.isBlank())?UUID.randomUUID().toString():incoming;
        var request=exchange.getRequest().mutate().headers(h->h.set("X-Request-ID",id)).build();
        exchange.getResponse().getHeaders().set("X-Request-ID",id);
        log.info("service=gateway requestId={} method={} path={}",id,request.getMethod(),request.getURI().getRawPath());
        return chain.filter(exchange.mutate().request(request).build())
            .doFinally(signal->log.info("service=gateway requestId={} result={} signal={}",
                id,exchange.getResponse().getStatusCode(),signal));
    }
}
