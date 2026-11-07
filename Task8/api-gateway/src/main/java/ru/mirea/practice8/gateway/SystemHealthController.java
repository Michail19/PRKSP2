package ru.mirea.practice8.gateway;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class SystemHealthController {
    private final WebClient client=WebClient.builder().build();
    private Mono<String> status(String service){
        return client.get().uri("http://"+service+":8080/actuator/health")
            .accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(String.class)
            .map(json->json.contains("\"status\":\"UP\"")?"UP":"DOWN")
            .timeout(Duration.ofSeconds(2))
            .onErrorReturn("DOWN");
    }
    @GetMapping("/api/system/health")
    public Mono<Map<String,String>> health(){
        return Mono.zip(status("supplier-service"),status("order-service"),status("delivery-service"))
            .map(t->Map.of("gateway","UP","supplier",t.getT1(),"order",t.getT2(),"delivery",t.getT3()));
    }
}
