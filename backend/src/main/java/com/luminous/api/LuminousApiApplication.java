package com.luminous.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.luminous.api")
@SpringBootApplication
public class LuminousApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LuminousApiApplication.class, args);
    }
}
