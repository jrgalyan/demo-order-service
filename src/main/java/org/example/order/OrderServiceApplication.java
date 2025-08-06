package org.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application for Order Service
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.example.order", "org.example.common"})
class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
