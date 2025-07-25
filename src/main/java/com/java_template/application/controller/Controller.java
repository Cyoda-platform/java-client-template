package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PerformanceReport;
import com.java_template.application.entity.ProductData;
import com.java_template.application.entity.ProductPerformanceJob;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // ---- ProductPerformanceJob Endpoints ----

    @PostMapping("/product-performance-jobs")
    public ResponseEntity<?> createProductPerformanceJob(@RequestBody @Valid ProductPerformanceJob job) throws JsonProcessingException, ExecutionException, InterruptedException {
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

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
    }

    @GetMapping("/product-performance-jobs/{id}")
    public ResponseEntity<?> getProductPerformanceJob(@PathVariable String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for ProductPerformanceJob ID: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        }

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

        ProductPerformanceJob job = objectMapper.treeToValue(node, ProductPerformanceJob.class);
        return ResponseEntity.ok(job);
    }

    // ---- ProductData Endpoints ----

    @GetMapping("/product-data/{id}")
    public ResponseEntity<?> getProductData(@PathVariable String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for ProductData ID: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        }

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

        ProductData productData = objectMapper.treeToValue(node, ProductData.class);
        return ResponseEntity.ok(productData);
    }

    // ---- PerformanceReport Endpoints ----

    @GetMapping("/performance-report/{id}")
    public ResponseEntity<?> getPerformanceReport(@PathVariable String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for PerformanceReport ID: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        }

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

        PerformanceReport report = objectMapper.treeToValue(node, PerformanceReport.class);
        return ResponseEntity.ok(report);
    }
}