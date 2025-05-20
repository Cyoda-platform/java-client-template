package com.java_template.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-products")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    public static class ScrapeRequest {
        @NotBlank
        public String username;
        @NotBlank
        public String password;
    }

    @PostMapping("/scrape")
    public CompletableFuture<ResponseEntity<String>> scrapeProducts(@RequestBody ScrapeRequest request) {
        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("username", request.username);
        entityNode.put("requestedAt", Instant.now().toString());
        entityNode.put("status", "processing");

        return entityService.addItem(
                "cyodaproduct",
                ENTITY_VERSION,
                entityNode
        ).thenApply(id -> ResponseEntity.ok("Scraping started with job id: " + id));
    }

    private List<ObjectNode> scrapeAndParseProducts() {
        log.info("Scraping and parsing products (mock implementation)");

        List<ObjectNode> products = new ArrayList<>();

        ObjectNode p1 = objectMapper.createObjectNode();
        p1.put("name", "Sauce Labs Backpack");
        p1.put("description", "carry all the things");
        p1.put("price", 29.99);
        p1.put("inventory", 1);
        products.add(p1);

        ObjectNode p2 = objectMapper.createObjectNode();
        p2.put("name", "Sauce Labs Bike Light");
        p2.put("description", "a light for your bike");
        p2.put("price", 9.99);
        p2.put("inventory", 1);
        products.add(p2);

        ObjectNode p3 = objectMapper.createObjectNode();
        p3.put("name", "Sauce Labs Bolt T-Shirt");
        p3.put("description", "soft and comfortable");
        p3.put("price", 15.99);
        p3.put("inventory", 1);
        products.add(p3);

        ObjectNode p4 = objectMapper.createObjectNode();
        p4.put("name", "Sauce Labs Fleece Jacket");
        p4.put("description", "warm fleece jacket");
        p4.put("price", 49.99);
        p4.put("inventory", 1);
        products.add(p4);

        return products;
    }

    private ObjectNode createSummaryReport(List<ObjectNode> products) {
        ObjectNode report = objectMapper.createObjectNode();

        int totalProducts = products.size();
        double totalValue = 0.0;
        ObjectNode highest = null;
        ObjectNode lowest = null;

        for (ObjectNode p : products) {
            double price = p.get("price").asDouble();
            int inventory = p.get("inventory").asInt();
            totalValue += price * inventory;
            if (highest == null || price > highest.get("price").asDouble()) highest = p;
            if (lowest == null || price < lowest.get("price").asDouble()) lowest = p;
        }

        double avgPrice = totalProducts > 0 ? totalValue / totalProducts : 0;

        report.put("totalProducts", totalProducts);
        report.put("averagePrice", round(avgPrice));
        report.set("highestPricedItem", highest != null ? highest.deepCopy() : null);
        report.set("lowestPricedItem", lowest != null ? lowest.deepCopy() : null);
        report.put("totalInventoryValue", round(totalValue));
        report.put("generatedAt", Instant.now().toString());

        return report;
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}