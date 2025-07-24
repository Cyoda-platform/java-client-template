package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Company;
import com.java_template.application.entity.CompanySearchJob;
import com.java_template.application.entity.LEIEnrichmentRequest;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // Local caches and counters replaced by EntityService, but kept counters for temporary usage in enrichment processing
    private final AtomicLong leiEnrichmentRequestIdCounter = new AtomicLong(1);
    private final AtomicLong companyIdCounter = new AtomicLong(1);

    // ---------------- CompanySearchJob Endpoints ----------------

    @PostMapping("/companySearchJob")
    public ResponseEntity<?> createCompanySearchJob(@RequestBody CompanySearchJob job) {
        try {
            if (job == null) {
                logger.error("Received null CompanySearchJob object");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is missing"));
            }
            if (job.getCompanyName() == null || job.getCompanyName().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "companyName is required"));
            }
            if (job.getOutputFormat() == null || job.getOutputFormat().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "outputFormat is required"));
            }

            job.setStatus("PENDING");
            job.setCreatedAt(Instant.now().toString());
            job.setCompletedAt(null);

            CompletableFuture<UUID> idFuture = entityService.addItem("CompanySearchJob", ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();
            String id = technicalId.toString();

            logger.info("Created CompanySearchJob with ID: {}", id);

            // Removed processCompanySearchJob call

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createCompanySearchJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in createCompanySearchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/companySearchJob/{id}")
    public ResponseEntity<?> getCompanySearchJob(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("CompanySearchJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CompanySearchJob not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getCompanySearchJob id={}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (Exception e) {
            logger.error("Error in getCompanySearchJob id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // ---------------- Company Endpoints ----------------

    @GetMapping("/company/{id}")
    public ResponseEntity<?> getCompany(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Company", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Company not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getCompany id={}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (Exception e) {
            logger.error("Error in getCompany id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // ---------------- LEIEnrichmentRequest Endpoints ----------------

    @GetMapping("/leiEnrichmentRequest/{id}")
    public ResponseEntity<?> getLEIEnrichmentRequest(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("LEIEnrichmentRequest", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "LEIEnrichmentRequest not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getLEIEnrichmentRequest id={}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (Exception e) {
            logger.error("Error in getLEIEnrichmentRequest id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }
}