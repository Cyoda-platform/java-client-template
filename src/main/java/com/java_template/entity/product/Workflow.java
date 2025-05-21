package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class Workflow {

    public CompletableFuture<ObjectNode> processProduct(ObjectNode entity) {
        logger.info("Workflow processProduct triggered for entity: {}", entity);

        // Workflow orchestration only: call processing steps sequentially
        return processAuthenticate(entity)
                .thenCompose(this::processScrape)
                .thenCompose(this::processAnalyze);
    }

    // Simulate authentication step (business logic)
    private CompletableFuture<ObjectNode> processAuthenticate(ObjectNode entity) {
        logger.info("processAuthenticate started");
        // Example: set authenticated flag or token in entity
        entity.put("authenticated", true);
        return CompletableFuture.completedFuture(entity);
    }

    // Simulate scraping step (business logic)
    private CompletableFuture<ObjectNode> processScrape(ObjectNode entity) {
        logger.info("processScrape started");
        // TODO: Replace with real scraping logic or call to scraping service
        // For prototype, just set scraped flag and mock data presence
        entity.put("scraped", true);
        entity.put("scrapedAt", Instant.now().toString());
        return CompletableFuture.completedFuture(entity);
    }

    // Simulate analysis step (business logic)
    private CompletableFuture<ObjectNode> processAnalyze(ObjectNode entity) {
        logger.info("processAnalyze started");

        // Fire and forget async job to update latest summary and job status
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting async scraping and analysis job from workflow");
                List<Product> products = scrapeInventoryPage();
                SummaryReport summary = analyzeProductsData(products);
                // Directly update volatile fields in controller or static holders as needed
                WorkflowStateHolder.latestSummary = summary;
                WorkflowStateHolder.latestJobStatus = new JobStatus("completed", Instant.now());
                logger.info("Async scraping and analysis job completed from workflow");
            } catch (Exception e) {
                WorkflowStateHolder.latestJobStatus = new JobStatus("failed", Instant.now());
                logger.error("Async job failed in workflow", e);
            }
        });

        // Update entity state directly to reflect the analysis started
        entity.put("analysisStartedAt", Instant.now().toString());
        entity.put("analysisStatus", "started");

        return CompletableFuture.completedFuture(entity);
    }

    // Mock helper methods and holders (would be elsewhere in real code)

    private List<Product> scrapeInventoryPage() {
        // Mocked list of products
        return List.of(
                new Product("Sauce Labs Backpack", "carry all the things", 29.99, 10),
                new Product("Sauce Labs Bike Light", "red light for bike", 9.99, 15)
        );
    }

    private SummaryReport analyzeProductsData(List<Product> products) {
        int total = products.size();
        double sumPrice = 0, totalInvValue = 0;
        ProductPriceInfo highest = null, lowest = null;
        for (Product p : products) {
            sumPrice += p.getPrice();
            totalInvValue += p.getPrice() * p.getInventory();
            if (highest == null || p.getPrice() > highest.getPrice()) {
                highest = new ProductPriceInfo(p.getItemName(), p.getPrice());
            }
            if (lowest == null || p.getPrice() < lowest.getPrice()) {
                lowest = new ProductPriceInfo(p.getItemName(), p.getPrice());
            }
        }
        double avg = total > 0 ? sumPrice / total : 0;
        return new SummaryReport(
                total,
                Math.round(avg * 100) / 100.0,
                highest,
                lowest,
                Math.round(totalInvValue * 100) / 100.0
        );
    }

    // Placeholder classes and static holder for shared state

    public static class WorkflowStateHolder {
        public static volatile SummaryReport latestSummary;
        public static volatile JobStatus latestJobStatus;
    }

    public record Product(String itemName, String description, double price, int inventory) {}

    public record SummaryReport(int totalProducts, double averagePrice, ProductPriceInfo highestPricedItem, ProductPriceInfo lowestPricedItem, double totalInventoryValue) {}

    public record ProductPriceInfo(String itemName, double price) {}

    public record JobStatus(String status, Instant requestedAt) {}
}