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

}