package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.RetrievalJob;
import com.java_template.application.entity.CompanyData;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, RetrievalJob> retrievalJobCache = new ConcurrentHashMap<>();
    private final AtomicLong retrievalJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, CompanyData> companyDataCache = new ConcurrentHashMap<>();
    private final AtomicLong companyDataIdCounter = new AtomicLong(1);

    // POST /prototype/retrievalJob - create a new RetrievalJob
    @PostMapping("/retrievalJob")
    public ResponseEntity<Map<String, String>> createRetrievalJob(@RequestBody RetrievalJob retrievalJob) {
        if (retrievalJob.getCompanyName() == null || retrievalJob.getCompanyName().isBlank()) {
            log.error("createRetrievalJob failed: companyName is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "companyName is required"));
        }
        retrievalJob.setStatus("PENDING");
        String technicalId = String.valueOf(retrievalJobIdCounter.getAndIncrement());
        retrievalJobCache.put(technicalId, retrievalJob);
        log.info("Created RetrievalJob with technicalId {}", technicalId);

        // Trigger processRetrievalJob
        processRetrievalJob(technicalId, retrievalJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/retrievalJob/{id} - get RetrievalJob by technicalId
    @GetMapping("/retrievalJob/{id}")
    public ResponseEntity<Object> getRetrievalJob(@PathVariable String id) {
        RetrievalJob job = retrievalJobCache.get(id);
        if (job == null) {
            log.error("RetrievalJob with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "RetrievalJob not found"));
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/companyData/{id} - get CompanyData by technicalId
    @GetMapping("/companyData/{id}")
    public ResponseEntity<Object> getCompanyData(@PathVariable String id) {
        CompanyData companyData = companyDataCache.get(id);
        if (companyData == null) {
            log.error("CompanyData with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CompanyData not found"));
        }
        return ResponseEntity.ok(companyData);
    }

    // Optional GET /prototype/companyData?businessId=xxx - search by businessId
    @GetMapping(value = "/companyData", params = "businessId")
    public ResponseEntity<List<CompanyData>> getCompanyDataByBusinessId(@RequestParam String businessId) {
        List<CompanyData> results = new ArrayList<>();
        for (CompanyData cd : companyDataCache.values()) {
            if (businessId.equals(cd.getBusinessId())) {
                results.add(cd);
            }
        }
        if (results.isEmpty()) {
            log.error("No CompanyData found with businessId {}", businessId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(results);
        }
        return ResponseEntity.ok(results);
    }

    // Processing method for RetrievalJob
    private void processRetrievalJob(String technicalId, RetrievalJob retrievalJob) {
        log.info("Processing RetrievalJob id: {}", technicalId);
        retrievalJob.setStatus("PROCESSING");

        try {
            // Validate companyName
            if (retrievalJob.getCompanyName() == null || retrievalJob.getCompanyName().isBlank()) {
                throw new IllegalArgumentException("companyName is blank");
            }

            // Query PRH Avoindata API (simulate)
            List<Map<String, String>> prhResults = queryPrhAvoindataApi(retrievalJob.getCompanyName());

            // Filter active companies
            List<Map<String, String>> activeCompanies = new ArrayList<>();
            for (Map<String, String> company : prhResults) {
                String status = company.getOrDefault("status", "Inactive");
                if ("Active".equalsIgnoreCase(status)) {
                    activeCompanies.add(company);
                }
            }

            // Enrich with LEI and create CompanyData entities
            for (Map<String, String> activeCompany : activeCompanies) {
                CompanyData cd = new CompanyData();
                cd.setBusinessId(activeCompany.get("businessId"));
                cd.setCompanyName(activeCompany.get("companyName"));
                cd.setCompanyType(activeCompany.get("companyType"));
                cd.setRegistrationDate(activeCompany.get("registrationDate"));
                cd.setStatus(activeCompany.get("status"));

                // Query LEI registry (simulate)
                String lei = queryLeiRegistry(cd.getBusinessId(), cd.getCompanyName());
                cd.setLei(lei != null ? lei : "Not Available");

                cd.setRetrievalJobId(technicalId);

                String companyDataId = String.valueOf(companyDataIdCounter.getAndIncrement());
                companyDataCache.put(companyDataId, cd);
                log.info("Created CompanyData with id {} for RetrievalJob {}", companyDataId, technicalId);
            }

            retrievalJob.setStatus("COMPLETED");
            log.info("RetrievalJob {} completed successfully", technicalId);
        } catch (Exception e) {
            retrievalJob.setStatus("FAILED");
            log.error("Failed to process RetrievalJob {}: {}", technicalId, e.getMessage());
        }
    }

    // Simulated external call to PRH Avoindata API
    private List<Map<String, String>> queryPrhAvoindataApi(String companyName) {
        // Simulate API call - return mock data
        List<Map<String, String>> result = new ArrayList<>();

        Map<String, String> company1 = new HashMap<>();
        company1.put("businessId", "1234567-8");
        company1.put("companyName", companyName + " Oyj");
        company1.put("companyType", "Limited Company");
        company1.put("registrationDate", "2000-01-01");
        company1.put("status", "Active");

        Map<String, String> company2 = new HashMap<>();
        company2.put("businessId", "8765432-1");
        company2.put("companyName", companyName + " Ltd");
        company2.put("companyType", "Limited Company");
        company2.put("registrationDate", "1990-05-05");
        company2.put("status", "Inactive");

        result.add(company1);
        result.add(company2);

        return result;
    }

    // Simulated external call to LEI registry
    private String queryLeiRegistry(String businessId, String companyName) {
        // Simulate LEI lookup - return mock LEI for demonstration
        if (businessId.equals("1234567-8")) {
            return "529900T8BM49AURSDO55";
        }
        return null;
    }
}