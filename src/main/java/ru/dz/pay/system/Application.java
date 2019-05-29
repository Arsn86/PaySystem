package ru.dz.pay.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        System.out.println("Need property: -Dspring.config.location=conf/application.properties");
        SpringApplication.run(Application.class, args);
    }
}
