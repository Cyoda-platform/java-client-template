package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Company;
import com.java_template.application.entity.CompanySearchJob;
import com.java_template.application.entity.LEIEnrichmentRequest;
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

            processCompanySearchJob(id, job);

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

    // ---------------- Process Methods ----------------

    private void processCompanySearchJob(String id, CompanySearchJob job) {
        logger.info("Processing CompanySearchJob with ID: {}", id);

        try {
            if (job.getCompanyName() == null || job.getCompanyName().isBlank()) {
                logger.error("CompanySearchJob ID {} has invalid companyName", id);
                job.setStatus("FAILED");
                updateCompanySearchJob(id, job);
                return;
            }
            String outputFormat = job.getOutputFormat().toUpperCase(Locale.ROOT);
            if (!outputFormat.equals("JSON") && !outputFormat.equals("CSV")) {
                logger.error("CompanySearchJob ID {} has invalid outputFormat: {}", id, outputFormat);
                job.setStatus("FAILED");
                updateCompanySearchJob(id, job);
                return;
            }

            job.setStatus("PROCESSING");
            updateCompanySearchJob(id, job);

            // Call PRH API to search companies by name
            List<Company> retrievedCompanies = searchCompaniesByName(job.getCompanyName());

            // Filter active companies only
            List<Company> activeCompanies = new ArrayList<>();
            for (Company c : retrievedCompanies) {
                if ("Active".equalsIgnoreCase(c.getStatus())) {
                    activeCompanies.add(c);
                }
            }

            for (Company company : activeCompanies) {
                // Create LEIEnrichmentRequest and process it
                LEIEnrichmentRequest leiRequest = new LEIEnrichmentRequest();
                leiRequest.setBusinessId(company.getBusinessId());
                leiRequest.setLeiSource(null);
                leiRequest.setStatus("PENDING");
                leiRequest.setLei("Not Available");

                // Add LEIEnrichmentRequest to EntityService
                UUID leiRequestTechnicalId = entityService.addItem("LEIEnrichmentRequest", ENTITY_VERSION, leiRequest).get();
                String leiRequestId = leiRequestTechnicalId.toString();

                processLEIEnrichmentRequest(leiRequestId, leiRequest);

                // Retrieve updated LEIEnrichmentRequest
                ObjectNode enrichmentNode = entityService.getItem("LEIEnrichmentRequest", ENTITY_VERSION, leiRequestTechnicalId).get();
                if (enrichmentNode != null && enrichmentNode.hasNonNull("lei")) {
                    company.setLei(enrichmentNode.get("lei").asText());
                }

                // Save enriched company with new technical id
                UUID companyTechnicalId = entityService.addItem("Company", ENTITY_VERSION, company).get();
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now().toString());
            updateCompanySearchJob(id, job);

            logger.info("Completed processing CompanySearchJob with ID: {}", id);
        } catch (Exception e) {
            logger.error("Error processing CompanySearchJob with ID: {}", id, e);
            try {
                job.setStatus("FAILED");
                updateCompanySearchJob(id, job);
            } catch (Exception ex) {
                logger.error("Failed to update CompanySearchJob status to FAILED for ID: {}", id, ex);
            }
        }
    }

    private void updateCompanySearchJob(String id, CompanySearchJob job) throws ExecutionException, InterruptedException {
        UUID technicalId = UUID.fromString(id);
        // EntityService has no update method; TODO: update operation not supported
        // For now, re-add item (this may create a new entry instead of update)
        // So skipping update as per instructions
    }

    private void processLEIEnrichmentRequest(String id, LEIEnrichmentRequest request) {
        logger.info("Processing LEIEnrichmentRequest with ID: {}", id);

        try {
            String lei = fetchLEIForBusinessId(request.getBusinessId());

            if (lei == null || lei.isBlank()) {
                request.setLei("Not Available");
            } else {
                request.setLei(lei);
                request.setLeiSource("GLEIF");
            }
            request.setStatus("COMPLETED");

            // No update operation, so TODO
            // TODO: update operation for LEIEnrichmentRequest is not supported by EntityService

            logger.info("Completed LEI enrichment for ID: {}", id);
        } catch (Exception e) {
            logger.error("Error processing LEIEnrichmentRequest with ID: {}", id, e);
            request.setStatus("FAILED");
            // TODO: update operation not supported
        }
    }

    // ---------------- External API Simulation ----------------

    private List<Company> searchCompaniesByName(String companyName) {
        logger.info("Simulating PRH API call for company name: {}", companyName);

        List<Company> companies = new ArrayList<>();

        if (companyName.toLowerCase(Locale.ROOT).contains("example")) {
            Company c1 = new Company();
            c1.setCompanyName("Example Oy");
            c1.setBusinessId("1234567-8");
            c1.setCompanyType("OY");
            c1.setRegistrationDate("2010-05-12");
            c1.setStatus("Active");
            c1.setLei("Not Available");
            companies.add(c1);

            Company c2 = new Company();
            c2.setCompanyName("Example Inactive Oy");
            c2.setBusinessId("9999999-9");
            c2.setCompanyType("OY");
            c2.setRegistrationDate("2005-03-15");
            c2.setStatus("Inactive");
            c2.setLei("Not Available");
            companies.add(c2);
        }

        return companies;
    }

    private String fetchLEIForBusinessId(String businessId) {
        logger.info("Simulating LEI API call for businessId: {}", businessId);

        if ("1234567-8".equals(businessId)) {
            return "5493001KJTIIGC8Y1R12";
        }
        return null;
    }
}