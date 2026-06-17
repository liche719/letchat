package com.letchat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableAsync//异步
@SpringBootApplication(scanBasePackages = "com.letchat")//启动类
@MapperScan("com.letchat.mappers")//mybatis
@EnableTransactionManagement//事务
@EnableScheduling//定时任务
public class LetChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(LetChatApplication.class, args);
    }
}
