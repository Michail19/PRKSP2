package ru.mirea.practice4.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

@SpringBootApplication
public class RSocketServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RSocketServerApplication.class, args);
    }

    @Bean
    public CsvCatalog csvCatalog() throws IOException {
        CsvCatalog catalog = CsvCatalog.fromResources();
        System.out.printf("[SERVER] Загружены CSV: darkstore=%d good=%d delivery=%d goods_list=%d%n",
                catalog.darkstoreCount(), catalog.goodCount(),
                catalog.deliveryCount(), catalog.goodsListCount());
        System.out.println("[SERVER] RSocket TCP ожидает подключения на localhost:7000");
        return catalog;
    }
}
