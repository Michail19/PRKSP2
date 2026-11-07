package ru.mirea.practice8.delivery;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;
class DeliveryTransitionsTest {
 @Test void allowedSequence(){
   assertTrue(DeliveryTransitions.allowed("CREATED","ACCEPTED"));
   assertTrue(DeliveryTransitions.allowed("ACCEPTED","IN_TRANSIT"));
   assertTrue(DeliveryTransitions.allowed("IN_TRANSIT","DELIVERED"));
 }
 @Test void forbiddenSkip(){
   assertFalse(DeliveryTransitions.allowed("CREATED","DELIVERED"));
   assertFalse(DeliveryTransitions.allowed("DELIVERED","CREATED"));
 }
 @Test void reactorValidation(){
   Mono<String> accepted=Mono.just("CREATED").filter(s->DeliveryTransitions.allowed(s,"ACCEPTED"))
       .map(s->"ACCEPTED");
   StepVerifier.create(accepted).expectNext("ACCEPTED").verifyComplete();
   StepVerifier.create(Mono.just("CREATED").filter(s->DeliveryTransitions.allowed(s,"DELIVERED")))
       .verifyComplete();
 }
}
