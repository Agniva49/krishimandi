package com.krishimandi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * KrishiMandi AI — Main Application Entry Point
 * AI-powered Crop Price Prediction Platform
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class KrishiMandiApplication {
    public static void main(String[] args) {
        SpringApplication.run(KrishiMandiApplication.class, args);
        System.out.println("""
            ╔══════════════════════════════════════╗
            ║   🌾  KrishiMandi AI  — Backend      ║
            ║   Crop Price Prediction Platform     ║
            ║   Running on http://localhost:8080   ║
            ╚══════════════════════════════════════╝
            """);
    }
}
