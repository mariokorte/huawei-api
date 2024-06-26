package de.mariokorte.huaweiapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SolarEnergyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SolarEnergyApplication.class, args);
    }
}