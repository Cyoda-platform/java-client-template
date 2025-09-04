package com.java_template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ABOUTME: Main Spring Boot application entry point that bootstraps the
 * Java client template with auto-configuration and component scanning.
 */

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
