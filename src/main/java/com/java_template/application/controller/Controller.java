package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Local counters kept for temporary usage in enrichment processing
    private final AtomicLong leiEnrichmentRequestIdCounter = new AtomicLong(1);
    private final AtomicLong companyIdCounter = new AtomicLong(1);

    // ---------------- CompanySearchJob Endpoints ----------------

    @PostMapping("/companySearchJob")
    public ResponseEntity<?> createCompanySearchJob(@Valid @RequestBody CompanySearchJob job) throws ExecutionException, InterruptedException {
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
            job.setCreatedAt(java.time.Instant.now().toString());
            job.setCompletedAt(null);

            CompletableFuture<UUID> idFuture = entityService.addItem("CompanySearchJob", ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();
            String id = technicalId.toString();

            logger.info("Created CompanySearchJob with ID: {}", id);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createCompanySearchJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error in createCompanySearchJob", e);
            throw e;
        } catch (Exception e) {
            logger.error("Error in createCompanySearchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/companySearchJob/{id}")
    public ResponseEntity<?> getCompanySearchJob(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("CompanySearchJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CompanySearchJob not found"));
            }
            CompanySearchJob job = objectMapper.treeToValue(node, CompanySearchJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getCompanySearchJob id={}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error in getCompanySearchJob id={}", id, e);
            throw e;
        } catch (JsonProcessingException e) {
            logger.error("JSON processing error in getCompanySearchJob id={}", id, e);
            throw e;
        } catch (Exception e) {
            logger.error("Error in getCompanySearchJob id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // ---------------- Company Endpoints ----------------

    @GetMapping("/company/{id}")
    public ResponseEntity<?> getCompany(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Company", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Company not found"));
            }
            Company company = objectMapper.treeToValue(node, Company.class);
            return ResponseEntity.ok(company);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getCompany id={}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error in getCompany id={}", id, e);
            throw e;
        } catch (JsonProcessingException e) {
            logger.error("JSON processing error in getCompany id={}", id, e);
            throw e;
        } catch (Exception e) {
            logger.error("Error in getCompany id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // ---------------- LEIEnrichmentRequest Endpoints ----------------

    @GetMapping("/leiEnrichmentRequest/{id}")
    public ResponseEntity<?> getLEIEnrichmentRequest(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("LEIEnrichmentRequest", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "LEIEnrichmentRequest not found"));
            }
            LEIEnrichmentRequest request = objectMapper.treeToValue(node, LEIEnrichmentRequest.class);
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getLEIEnrichmentRequest id={}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error in getLEIEnrichmentRequest id={}", id, e);
            throw e;
        } catch (JsonProcessingException e) {
            logger.error("JSON processing error in getLEIEnrichmentRequest id={}", id, e);
            throw e;
        } catch (Exception e) {
            logger.error("Error in getLEIEnrichmentRequest id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }
}