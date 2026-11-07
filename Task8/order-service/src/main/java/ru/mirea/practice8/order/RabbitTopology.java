package ru.mirea.practice8.order;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitTopology {
  public static final String EXCHANGE="orders.exchange";
  public static final String QUEUE="delivery.order-created";
  public static final String KEY="order.created";
  @Bean DirectExchange ordersExchange(){return new DirectExchange(EXCHANGE,true,false);}
  @Bean Queue deliveryQueue(){return new Queue(QUEUE,true);}
  @Bean Binding orderCreatedBinding(Queue deliveryQueue,DirectExchange ordersExchange){
     return BindingBuilder.bind(deliveryQueue).to(ordersExchange).with(KEY);
  }
}
