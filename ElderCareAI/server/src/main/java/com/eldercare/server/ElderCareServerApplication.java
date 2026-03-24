package com.eldercare.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ElderCareServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ElderCareServerApplication.class, args);
    }
}
