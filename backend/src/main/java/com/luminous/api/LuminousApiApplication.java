package com.luminous.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.luminous.api", markerInterface = com.baomidou.mybatisplus.core.mapper.BaseMapper.class)
@SpringBootApplication
public class LuminousApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LuminousApiApplication.class, args);
    }
}
