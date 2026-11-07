package ru.mirea.practice8.delivery;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitTopology {
    @Bean DirectExchange ordersExchange(){return new DirectExchange("orders.exchange",true,false);}
    @Bean Queue deliveryQueue(){return new Queue("delivery.order-created",true);}
    @Bean Binding orderCreatedBinding(Queue deliveryQueue,DirectExchange ordersExchange){
        return BindingBuilder.bind(deliveryQueue).to(ordersExchange).with("order.created");
    }
}
