package com.telecom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TelecomUploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelecomUploadApplication.class, args);
    }
}
