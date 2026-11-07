package ru.mirea.practice8.supplier;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SupplierController {
    private static final Logger log=LoggerFactory.getLogger(SupplierController.class);
    private final JdbcTemplate jdbc;
    public SupplierController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Product(long id,long supplierId,String name,long priceKopecks,int stock){}
    public record Supplier(long id,String name){}
    public record Availability(long productId,boolean available,int stock,long unitPriceKopecks){}
    private static final org.springframework.jdbc.core.RowMapper<Product> PRODUCT=(rs,i)->new Product(
        rs.getLong("id"),rs.getLong("supplier_id"),rs.getString("name"),rs.getLong("price_kopecks"),rs.getInt("stock"));
    @GetMapping("/api/products")
    public List<Product> all(){return jdbc.query("SELECT * FROM products ORDER BY id",PRODUCT);}
    @GetMapping("/api/products/{id}")
    public Product one(@PathVariable long id){return find(id);}
    @GetMapping("/api/suppliers")
    public List<Supplier> suppliers(){return jdbc.query("SELECT id,name FROM suppliers ORDER BY id",
        (r,i)->new Supplier(r.getLong(1),r.getString(2)));}
    @GetMapping("/internal/products/{id}/availability")
    public Availability availability(@PathVariable long id,@RequestParam int quantity,
                                      @RequestHeader(value="X-Request-ID",defaultValue="-") String rid){
        if(quantity<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity must be positive");
        Product p=find(id);
        log.info("service=supplier requestId={} check product={} quantity={} stock={}",rid,id,quantity,p.stock());
        return new Availability(p.id(),p.stock()>=quantity,p.stock(),p.priceKopecks());
    }
    private Product find(long id){
        return jdbc.query("SELECT * FROM products WHERE id=?",PRODUCT,id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found: "+id));
    }
}
