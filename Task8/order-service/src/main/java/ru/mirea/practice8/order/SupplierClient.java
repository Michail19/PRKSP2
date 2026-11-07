package ru.mirea.practice8.order;
import java.time.Duration;
import java.util.function.Supplier;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupplierClient {
    private static final Logger log=LoggerFactory.getLogger(SupplierClient.class);
    private final RestClient client;
    private final CircuitBreaker breaker;
    private final Retry retry;
    public SupplierClient(RestClient.Builder builder,@Value("${app.supplier.base-url}") String supplierUrl){
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(600);
        factory.setReadTimeout(900);
        client=builder.baseUrl(supplierUrl).requestFactory(factory).build();
        breaker=CircuitBreaker.of("supplier",CircuitBreakerConfig.custom()
            .slidingWindowSize(4).minimumNumberOfCalls(2).failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(8))
            .recordExceptions(ResourceAccessException.class,HttpServerErrorException.class).build());
        retry=Retry.of("supplier",RetryConfig.custom()
            .maxAttempts(2).waitDuration(Duration.ofMillis(250))
            .retryOnException(ex->ex instanceof ResourceAccessException || ex instanceof HttpServerErrorException).build());
    }
    public Availability check(long productId,int quantity,String requestId){
        Supplier<Availability> call=()->client.get().uri(uri->uri.path("/internal/products/{id}/availability")
                .queryParam("quantity",quantity).build(productId))
            .header("X-Request-ID",requestId).retrieve().body(Availability.class);
        try {
            Availability result=Retry.decorateSupplier(retry,CircuitBreaker.decorateSupplier(breaker,call)).get();
            log.info("service=order requestId={} supplier response product={} available={} circuit={}",
                requestId,productId,result.available(),breaker.getState());
            return result;
        } catch(HttpClientErrorException ex){
            if(ex.getStatusCode().value()==404)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown productId",ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid supplier query",ex);
        } catch(ResourceAccessException|HttpServerErrorException|CallNotPermittedException ex){
            log.warn("service=order requestId={} supplier unavailable circuit={} cause={}",requestId,breaker.getState(),ex.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Supplier unavailable (timeout/retry/circuit breaker)",ex);
        }
    }
    public String breakerState(){return breaker.getState().name();}
}
