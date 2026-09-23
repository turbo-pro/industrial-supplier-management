package io.github.turbopro.ism;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IndustrialSupplierManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndustrialSupplierManagementApplication.class, args);
    }
}
