package com.example.k3sdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class K3sDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(K3sDemoApplication.class, args);
    }
}
