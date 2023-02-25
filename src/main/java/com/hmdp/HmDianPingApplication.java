package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class HmDianPingApplication {

    //初始版本

    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }

}
