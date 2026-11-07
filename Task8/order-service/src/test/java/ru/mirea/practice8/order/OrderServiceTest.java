package ru.mirea.practice8.order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderServiceTest {
 @Test void rejectsNonPositiveQuantityWithoutRemoteCall(){
   SupplierClient client=mock(SupplierClient.class);
   OrderService service=new OrderService(mock(JdbcTemplate.class),client,new ObjectMapper());
   ResponseStatusException ex=assertThrows(ResponseStatusException.class,
        ()->service.create(new NewOrder(1,0),"req-test"));
   assertEquals(400,ex.getStatusCode().value());
   verifyNoInteractions(client);
 }
 @Test void rejectsInsufficientStock(){
   SupplierClient client=mock(SupplierClient.class);
   when(client.check(1,99,"req-test")).thenReturn(new Availability(1,false,3,59999));
   OrderService service=new OrderService(mock(JdbcTemplate.class),client,new ObjectMapper());
   ResponseStatusException ex=assertThrows(ResponseStatusException.class,
        ()->service.create(new NewOrder(1,99),"req-test"));
   assertEquals(409,ex.getStatusCode().value());
 }
}
