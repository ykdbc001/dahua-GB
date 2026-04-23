package com.example.firealarm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 消防报警系统主应用类
 * 基于GB/T 26875.3-2011国标协议实现
 */
@SpringBootApplication
public class FireAlarmApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireAlarmApplication.class, args);
    }
}