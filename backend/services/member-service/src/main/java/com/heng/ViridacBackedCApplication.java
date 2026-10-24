package com.heng;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.heng.mapper")
@EnableScheduling
public class ViridacBackedCApplication {

    public static void main(String[] args) {
        SpringApplication.run(ViridacBackedCApplication.class, args);
    }

}
