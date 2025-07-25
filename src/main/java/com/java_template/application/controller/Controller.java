package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PerformanceReport;
import com.java_template.application.entity.ProductData;
import com.java_template.application.entity.ProductPerformanceJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final AtomicLong productPerformanceJobIdCounter = new AtomicLong(1);
    private final AtomicLong productDataIdCounter = new AtomicLong(1);
    private final AtomicLong performanceReportIdCounter = new AtomicLong(1);

    // ---- ProductPerformanceJob Endpoints ----

    @PostMapping("/product-performance-jobs")
    public ResponseEntity<?> createProductPerformanceJob(@RequestBody ProductPerformanceJob job) {
        try {
            if (job == null || !job.isValid()) {
                logger.error("Invalid ProductPerformanceJob received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ProductPerformanceJob data"));
            }
            job.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "ProductPerformanceJob",
                    ENTITY_VERSION,
                    job
            );

            UUID technicalId = idFuture.get();

            logger.info("Created ProductPerformanceJob with ID: {}", technicalId);

            processProductPerformanceJob(technicalId.toString(), job);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createProductPerformanceJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating ProductPerformanceJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/product-performance-jobs/{id}")
    public ResponseEntity<?> getProductPerformanceJob(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "ProductPerformanceJob",
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("ProductPerformanceJob not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ProductPerformanceJob not found"));
            }
            return ResponseEntity.ok(node);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for ProductPerformanceJob ID: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (Exception e) {
            logger.error("Error retrieving ProductPerformanceJob with ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // ---- ProductData Endpoints ----

    @GetMapping("/product-data/{id}")
    public ResponseEntity<?> getProductData(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "ProductData",
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("ProductData not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ProductData not found"));
            }
            return ResponseEntity.ok(node);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for ProductData ID: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (Exception e) {
            logger.error("Error retrieving ProductData with ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // ---- PerformanceReport Endpoints ----

    @GetMapping("/performance-report/{id}")
    public ResponseEntity<?> getPerformanceReport(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "PerformanceReport",
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("PerformanceReport not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PerformanceReport not found"));
            }
            return ResponseEntity.ok(node);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for PerformanceReport ID: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (Exception e) {
            logger.error("Error retrieving PerformanceReport with ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // ---- Processing Methods ----

    private void processProductPerformanceJob(String technicalId, ProductPerformanceJob job) {
        logger.info("Processing ProductPerformanceJob with ID: {}", technicalId);

        try {
            // 1. Validation: scheduledDay should be MONDAY, emailRecipient valid format (basic check)
            if (!"MONDAY".equalsIgnoreCase(job.getScheduledDay())) {
                job.setStatus("FAILED");
                logger.error("Job {} failed validation: scheduledDay is not MONDAY", technicalId);
                return;
            }
            if (job.getEmailRecipient() == null || job.getEmailRecipient().isBlank() || !job.getEmailRecipient().contains("@")) {
                job.setStatus("FAILED");
                logger.error("Job {} failed validation: invalid emailRecipient", technicalId);
                return;
            }
            job.setStatus("PROCESSING");

            // 2. Data Extraction: Simulate fetching all product data from Pet Store API
            List<ProductData> fetchedProducts = fetchProductDataFromPetStoreAPI();

            // 3. Data Persistence: Save immutable ProductData entities with new UUIDs via EntityService
            List<ProductData> productsToAdd = new ArrayList<>(fetchedProducts);
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    "ProductData",
                    ENTITY_VERSION,
                    productsToAdd
            );
            List<UUID> productDataIds = idsFuture.get();

            // 4. Analysis: Calculate KPIs (basic aggregation simulation)
            PerformanceReport report = generatePerformanceReport(job, fetchedProducts);

            // 5. Save PerformanceReport with new technical ID
            CompletableFuture<UUID> reportIdFuture = entityService.addItem(
                    "PerformanceReport",
                    ENTITY_VERSION,
                    report
            );
            UUID reportId = reportIdFuture.get();

            // 6. Email Dispatch: Simulate sending email
            boolean emailSent = sendEmailWithReport(report, job.getEmailRecipient());

            if (emailSent) {
                job.setStatus("COMPLETED");
                logger.info("ProductPerformanceJob {} COMPLETED successfully", technicalId);
            } else {
                job.setStatus("FAILED");
                logger.error("ProductPerformanceJob {} failed: email dispatch failure", technicalId);
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            logger.error("ProductPerformanceJob {} failed with exception: {}", technicalId, e.getMessage(), e);
        }
    }

    private List<ProductData> fetchProductDataFromPetStoreAPI() {
        // Simulated API fetch logic - in real scenario, call external API
        List<ProductData> products = new ArrayList<>();

        ProductData p1 = new ProductData();
        p1.setProductId("p001");
        p1.setName("Cat Food");
        p1.setCategory("Food");
        p1.setSalesVolume(150);
        p1.setRevenue(2250.00);
        p1.setInventoryCount(75);
        products.add(p1);

        ProductData p2 = new ProductData();
        p2.setProductId("p002");
        p2.setName("Dog Toy");
        p2.setCategory("Toys");
        p2.setSalesVolume(100);
        p2.setRevenue(1500.00);
        p2.setInventoryCount(50);
        products.add(p2);

        ProductData p3 = new ProductData();
        p3.setProductId("p003");
        p3.setName("Bird Cage");
        p3.setCategory("Accessories");
        p3.setSalesVolume(20);
        p3.setRevenue(800.00);
        p3.setInventoryCount(5);
        products.add(p3);

        return products;
    }

    private PerformanceReport generatePerformanceReport(ProductPerformanceJob job, List<ProductData> products) {
        PerformanceReport report = new PerformanceReport();
        report.setJobTechnicalId(null); // will be set by caller if needed
        report.setGeneratedAt(LocalDateTime.now());

        // Summary generation: identify top products and low inventory items
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("Top products: ");

        // Sort products by salesVolume desc
        products.sort(Comparator.comparing(ProductData::getSalesVolume).reversed());
        List<ProductData> topProducts = products.subList(0, Math.min(2, products.size()));
        for (int i = 0; i < topProducts.size(); i++) {
            summaryBuilder.append(topProducts.get(i).getName());
            if (i < topProducts.size() - 1) summaryBuilder.append(", ");
        }

        summaryBuilder.append("; Inventory low for ");
        // Find products with inventoryCount < 10
        List<ProductData> lowInventory = new ArrayList<>();
        for (ProductData pd : products) {
            if (pd.getInventoryCount() < 10) lowInventory.add(pd);
        }
        for (int i = 0; i < lowInventory.size(); i++) {
            summaryBuilder.append(lowInventory.get(i).getName());
            if (i < lowInventory.size() - 1) summaryBuilder.append(", ");
        }
        if (lowInventory.isEmpty()) {
            summaryBuilder.append("none");
        }
        summaryBuilder.append(".");

        report.setSummary(summaryBuilder.toString());
        // Simulate report file URL
        report.setReportFileUrl("https://reports.cyoda.com/report" + System.currentTimeMillis() + ".pdf");
        return report;
    }

    private boolean sendEmailWithReport(PerformanceReport report, String emailRecipient) {
        // Simulate email sending logic - always succeed in prototype
        logger.info("Sending email to {} with report summary: {}", emailRecipient, report.getSummary());
        return true;
    }
}