```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api/inventory")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Mock inventory data
    private static final Map<String, InventoryReport> inventoryData = new ConcurrentHashMap<>();

    @PostMapping("/fetch") // must be first
    public ResponseEntity<String> fetchInventoryData(@RequestBody @Valid InventoryFilter filter) {
        String jobId = "job_" + System.currentTimeMillis();
        entityJobs.put(jobId, new JobStatus("processing", System.currentTimeMillis()));

        // Fire-and-forget the processing
        CompletableFuture.runAsync(() -> {
            try {
                RestTemplate restTemplate = new RestTemplate();
                String apiUrl = "https://app.swaggerhub.com/apis/CGIANNAROS/Test/1.0.0#/developers/searchInventory";
                // TODO: Replace with actual API call and processing logic
                ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, filter, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                    // Process the JSON response and populate inventoryData
                    // TODO: Replace with actual inventory data processing
                }
                entityJobs.put(jobId, new JobStatus("completed", System.currentTimeMillis()));
            } catch (Exception e) {
                logger.error("Error fetching inventory data", e);
                entityJobs.put(jobId, new JobStatus("failed", System.currentTimeMillis()));
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Job initiated with ID: " + jobId);
    }

    @GetMapping("/report") // must be first
    public ResponseEntity<InventoryReport> getReport() {
        // TODO: Replace with actual report generation logic
        InventoryReport report = new InventoryReport(150, 250.75, 37500, Map.of("electronics", 50, "furniture", 100));
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException e) {
        logger.error("Error: {}", e.getMessage());
        return ResponseEntity.status(e.getStatusCode()).body("Error: " + e.getStatusCode().toString());
    }

    // Entity model classes
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InventoryFilter {
        @NotBlank
        private String category;

        @NotNull
        private Integer minPrice;

        @NotNull
        private Integer maxPrice;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InventoryReport {
        private int totalItems;
        private double averagePrice;
        private int totalValue;
        private Map<String, Integer> categoryDistribution;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class JobStatus {
        private String status;
        private long timestamp;
    }
}
```