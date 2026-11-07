package ru.mirea.practice8.supplier;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SupplierControllerTest {
 @Test void rejectsInvalidQuantity(){
   var jdbc=mock(JdbcTemplate.class);
   var controller=new SupplierController(jdbc);
   var ex=assertThrows(ResponseStatusException.class,()->controller.availability(1,0,"test-rid"));
   assertEquals(400,ex.getStatusCode().value());
   verifyNoInteractions(jdbc);
 }
}
