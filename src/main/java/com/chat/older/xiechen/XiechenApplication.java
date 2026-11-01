package com.chat.older.xiechen;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;

@MapperScan("com.chat.older.xiechen.dao")
@SpringBootApplication(exclude = MybatisPlusAutoConfiguration.class)
@EnableScheduling
public class XiechenApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiechenApplication.class, args);
    }

}
