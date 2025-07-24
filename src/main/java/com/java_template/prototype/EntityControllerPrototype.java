package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.java_template.application.entity.CompanySearchJob;
import com.java_template.application.entity.Company;
import com.java_template.application.entity.LEIEnrichmentRequest;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counter for CompanySearchJob (Orchestration Entity)
    private final ConcurrentHashMap<String, CompanySearchJob> companySearchJobCache = new ConcurrentHashMap<>();
    private final AtomicLong companySearchJobIdCounter = new AtomicLong(1);

    // Cache and ID counter for Company (Business Entity)
    private final ConcurrentHashMap<String, Company> companyCache = new ConcurrentHashMap<>();
    private final AtomicLong companyIdCounter = new AtomicLong(1);

    // Cache and ID counter for LEIEnrichmentRequest (Business Entity)
    private final ConcurrentHashMap<String, LEIEnrichmentRequest> leiEnrichmentRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong leiEnrichmentRequestIdCounter = new AtomicLong(1);

    // ---------------- CompanySearchJob Endpoints ----------------

    @PostMapping("/companySearchJob")
    public ResponseEntity<Map<String, String>> createCompanySearchJob(@RequestBody CompanySearchJob job) {
        if (job == null) {
            log.error("Received null CompanySearchJob object");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is missing"));
        }
        // Basic validation for required fields except status/createdAt/completedAt which are managed internally
        if (job.getCompanyName() == null || job.getCompanyName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "companyName is required"));
        }
        if (job.getOutputFormat() == null || job.getOutputFormat().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "outputFormat is required"));
        }
        String id = String.valueOf(companySearchJobIdCounter.getAndIncrement());
        job.setStatus("PENDING");
        job.setCreatedAt(java.time.Instant.now().toString());
        job.setCompletedAt(null);
        companySearchJobCache.put(id, job);

        log.info("Created CompanySearchJob with ID: {}", id);

        processCompanySearchJob(id, job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/companySearchJob/{id}")
    public ResponseEntity<?> getCompanySearchJob(@PathVariable String id) {
        CompanySearchJob job = companySearchJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CompanySearchJob not found"));
        }
        return ResponseEntity.ok(job);
    }

    // ---------------- Company Endpoints ----------------

    @GetMapping("/company/{id}")
    public ResponseEntity<?> getCompany(@PathVariable String id) {
        Company company = companyCache.get(id);
        if (company == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Company not found"));
        }
        return ResponseEntity.ok(company);
    }

    // ---------------- LEIEnrichmentRequest Endpoints ----------------

    @GetMapping("/leiEnrichmentRequest/{id}")
    public ResponseEntity<?> getLEIEnrichmentRequest(@PathVariable String id) {
        LEIEnrichmentRequest request = leiEnrichmentRequestCache.get(id);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "LEIEnrichmentRequest not found"));
        }
        return ResponseEntity.ok(request);
    }

    // ---------------- Process Methods ----------------

    private void processCompanySearchJob(String id, CompanySearchJob job) {
        log.info("Processing CompanySearchJob with ID: {}", id);

        // Validate companyName and outputFormat
        if (job.getCompanyName() == null || job.getCompanyName().isBlank()) {
            log.error("CompanySearchJob ID {} has invalid companyName", id);
            job.setStatus("FAILED");
            companySearchJobCache.put(id, job);
            return;
        }
        String outputFormat = job.getOutputFormat().toUpperCase(Locale.ROOT);
        if (!outputFormat.equals("JSON") && !outputFormat.equals("CSV")) {
            log.error("CompanySearchJob ID {} has invalid outputFormat: {}", id, outputFormat);
            job.setStatus("FAILED");
            companySearchJobCache.put(id, job);
            return;
        }

        job.setStatus("PROCESSING");
        companySearchJobCache.put(id, job);

        try {
            // Call PRH API to search companies by name
            List<Company> retrievedCompanies = searchCompaniesByName(job.getCompanyName());

            // Filter active companies only
            List<Company> activeCompanies = new ArrayList<>();
            for (Company c : retrievedCompanies) {
                if ("Active".equalsIgnoreCase(c.getStatus())) {
                    activeCompanies.add(c);
                }
            }

            // For each active company, create LEIEnrichmentRequest and process it
            for (Company company : activeCompanies) {
                String leiRequestId = String.valueOf(leiEnrichmentRequestIdCounter.getAndIncrement());
                LEIEnrichmentRequest leiRequest = new LEIEnrichmentRequest();
                leiRequest.setBusinessId(company.getBusinessId());
                leiRequest.setLeiSource(null);
                leiRequest.setStatus("PENDING");
                leiRequest.setLei("Not Available");
                leiEnrichmentRequestCache.put(leiRequestId, leiRequest);

                processLEIEnrichmentRequest(leiRequestId, leiRequest);

                // After enrichment, update company lei field from enrichment
                LEIEnrichmentRequest enrichedRequest = leiEnrichmentRequestCache.get(leiRequestId);
                if (enrichedRequest != null && enrichedRequest.getLei() != null) {
                    company.setLei(enrichedRequest.getLei());
                }

                // Save enriched company with new technical id
                String companyId = String.valueOf(companyIdCounter.getAndIncrement());
                companyCache.put(companyId, company);
            }

            // Mark job completed
            job.setStatus("COMPLETED");
            job.setCompletedAt(java.time.Instant.now().toString());
            companySearchJobCache.put(id, job);

            log.info("Completed processing CompanySearchJob with ID: {}", id);
        } catch (Exception e) {
            log.error("Error processing CompanySearchJob with ID: {}", id, e);
            job.setStatus("FAILED");
            companySearchJobCache.put(id, job);
        }
    }

    private void processLEIEnrichmentRequest(String id, LEIEnrichmentRequest request) {
        log.info("Processing LEIEnrichmentRequest with ID: {}", id);

        try {
            // Query external LEI sources (simulate with dummy data)
            String lei = fetchLEIForBusinessId(request.getBusinessId());

            if (lei == null || lei.isBlank()) {
                request.setLei("Not Available");
            } else {
                request.setLei(lei);
                request.setLeiSource("GLEIF");
            }
            request.setStatus("COMPLETED");
            leiEnrichmentRequestCache.put(id, request);

            log.info("Completed LEI enrichment for ID: {}", id);
        } catch (Exception e) {
            log.error("Error processing LEIEnrichmentRequest with ID: {}", id, e);
            request.setStatus("FAILED");
            leiEnrichmentRequestCache.put(id, request);
        }
    }

    // ---------------- External API Simulation ----------------

    private List<Company> searchCompaniesByName(String companyName) {
        // Simulate external API call to PRH Avoindata YTJ API
        log.info("Simulating PRH API call for company name: {}", companyName);

        // Dummy data for prototype purpose
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
        // Simulate external LEI API call to GLEIF or other source
        log.info("Simulating LEI API call for businessId: {}", businessId);

        // Dummy data for prototype
        if ("1234567-8".equals(businessId)) {
            return "5493001KJTIIGC8Y1R12";
        }
        return null;
    }
}