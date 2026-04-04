package com.jacolp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class MiddlewareServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiddlewareServerApplication.class, args);
    }

}
