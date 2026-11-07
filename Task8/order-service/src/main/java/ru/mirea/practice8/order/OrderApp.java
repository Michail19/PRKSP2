package ru.mirea.practice8.order;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class OrderApp {
    public static void main(String[] args){SpringApplication.run(OrderApp.class,args);}
}
