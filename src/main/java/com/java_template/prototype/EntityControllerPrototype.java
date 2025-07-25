package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.ProductPerformanceJob;
import com.java_template.application.entity.ProductData;
import com.java_template.application.entity.PerformanceReport;
import java.time.LocalDateTime;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, ProductPerformanceJob> productPerformanceJobCache = new ConcurrentHashMap<>();
    private final AtomicLong productPerformanceJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, ProductData> productDataCache = new ConcurrentHashMap<>();
    private final AtomicLong productDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, PerformanceReport> performanceReportCache = new ConcurrentHashMap<>();
    private final AtomicLong performanceReportIdCounter = new AtomicLong(1);

    // ---- ProductPerformanceJob Endpoints ----

    @PostMapping("/product-performance-jobs")
    public ResponseEntity<Map<String, String>> createProductPerformanceJob(@RequestBody ProductPerformanceJob job) {
        if (job == null || !job.isValid()) {
            log.error("Invalid ProductPerformanceJob received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ProductPerformanceJob data"));
        }
        String id = String.valueOf(productPerformanceJobIdCounter.getAndIncrement());
        job.setStatus("PENDING");
        productPerformanceJobCache.put(id, job);
        log.info("Created ProductPerformanceJob with ID: {}", id);
        processProductPerformanceJob(id, job);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/product-performance-jobs/{id}")
    public ResponseEntity<?> getProductPerformanceJob(@PathVariable String id) {
        ProductPerformanceJob job = productPerformanceJobCache.get(id);
        if (job == null) {
            log.error("ProductPerformanceJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ProductPerformanceJob not found"));
        }
        return ResponseEntity.ok(job);
    }

    // ---- ProductData Endpoints ----

    @GetMapping("/product-data/{id}")
    public ResponseEntity<?> getProductData(@PathVariable String id) {
        ProductData data = productDataCache.get(id);
        if (data == null) {
            log.error("ProductData not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ProductData not found"));
        }
        return ResponseEntity.ok(data);
    }

    // ---- PerformanceReport Endpoints ----

    @GetMapping("/performance-report/{id}")
    public ResponseEntity<?> getPerformanceReport(@PathVariable String id) {
        PerformanceReport report = performanceReportCache.get(id);
        if (report == null) {
            log.error("PerformanceReport not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PerformanceReport not found"));
        }
        return ResponseEntity.ok(report);
    }

    // ---- Processing Methods ----

    private void processProductPerformanceJob(String technicalId, ProductPerformanceJob job) {
        log.info("Processing ProductPerformanceJob with ID: {}", technicalId);

        try {
            // 1. Validation: scheduledDay should be MONDAY, emailRecipient valid format (basic check)
            if (!"MONDAY".equalsIgnoreCase(job.getScheduledDay())) {
                job.setStatus("FAILED");
                log.error("Job {} failed validation: scheduledDay is not MONDAY", technicalId);
                return;
            }
            if (job.getEmailRecipient() == null || job.getEmailRecipient().isBlank() || !job.getEmailRecipient().contains("@")) {
                job.setStatus("FAILED");
                log.error("Job {} failed validation: invalid emailRecipient", technicalId);
                return;
            }
            job.setStatus("PROCESSING");

            // 2. Data Extraction: Simulate fetching all product data from Pet Store API
            // Here we simulate with dummy data for prototype
            List<ProductData> fetchedProducts = fetchProductDataFromPetStoreAPI();

            // 3. Data Persistence: Save immutable ProductData entities with new IDs
            for (ProductData pd : fetchedProducts) {
                String prodId = String.valueOf(productDataIdCounter.getAndIncrement());
                productDataCache.put(prodId, pd);
            }

            // 4. Analysis: Calculate KPIs (basic aggregation simulation)
            PerformanceReport report = generatePerformanceReport(job, fetchedProducts);

            // 5. Save PerformanceReport with new technical ID
            String reportId = String.valueOf(performanceReportIdCounter.getAndIncrement());
            performanceReportCache.put(reportId, report);

            // 6. Email Dispatch: Simulate sending email
            boolean emailSent = sendEmailWithReport(report, job.getEmailRecipient());

            if (emailSent) {
                job.setStatus("COMPLETED");
                log.info("ProductPerformanceJob {} COMPLETED successfully", technicalId);
            } else {
                job.setStatus("FAILED");
                log.error("ProductPerformanceJob {} failed: email dispatch failure", technicalId);
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("ProductPerformanceJob {} failed with exception: {}", technicalId, e.getMessage());
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
        log.info("Sending email to {} with report summary: {}", emailRecipient, report.getSummary());
        return true;
    }
}