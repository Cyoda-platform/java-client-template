package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.CurrencyRateJob;
import com.java_template.application.entity.CurrencyRate;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counter for CurrencyRateJob (Orchestration entity)
    private final ConcurrentHashMap<String, CurrencyRateJob> currencyRateJobCache = new ConcurrentHashMap<>();
    private final AtomicLong currencyRateJobIdCounter = new AtomicLong(1);

    // Cache and ID counter for CurrencyRate (Business entity)
    private final ConcurrentHashMap<String, CurrencyRate> currencyRateCache = new ConcurrentHashMap<>();
    private final AtomicLong currencyRateIdCounter = new AtomicLong(1);

    // POST /prototype/currencyRateJob - create CurrencyRateJob
    @PostMapping("/currencyRateJob")
    public ResponseEntity<Map<String, String>> createCurrencyRateJob(@RequestBody CurrencyRateJob job) {
        if (!job.isValid()) {
            log.error("Invalid CurrencyRateJob data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String id = "job-" + currencyRateJobIdCounter.getAndIncrement();
        job.setStatus("PENDING");
        currencyRateJobCache.put(id, job);
        log.info("Created CurrencyRateJob with id {}", id);

        processCurrencyRateJob(id, job);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/currencyRateJob/{id} - retrieve CurrencyRateJob by technicalId
    @GetMapping("/currencyRateJob/{id}")
    public ResponseEntity<CurrencyRateJob> getCurrencyRateJob(@PathVariable String id) {
        CurrencyRateJob job = currencyRateJobCache.get(id);
        if (job == null) {
            log.error("CurrencyRateJob not found for id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/currencyRate/{id} - retrieve CurrencyRate by technicalId
    @GetMapping("/currencyRate/{id}")
    public ResponseEntity<CurrencyRate> getCurrencyRate(@PathVariable String id) {
        CurrencyRate rate = currencyRateCache.get(id);
        if (rate == null) {
            log.error("CurrencyRate not found for id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(rate);
    }

    // Optional GET with filters - only if explicitly requested
    @GetMapping("/currencyRate")
    public ResponseEntity<List<CurrencyRate>> getCurrencyRatesByCondition(@RequestParam(required = false) String currencyFrom,
                                                                          @RequestParam(required = false) String currencyTo) {
        List<CurrencyRate> results = new ArrayList<>();
        for (CurrencyRate rate : currencyRateCache.values()) {
            boolean matches = true;
            if (currencyFrom != null && !currencyFrom.isBlank()) {
                matches = matches && currencyFrom.equalsIgnoreCase(rate.getCurrencyFrom());
            }
            if (currencyTo != null && !currencyTo.isBlank()) {
                matches = matches && currencyTo.equalsIgnoreCase(rate.getCurrencyTo());
            }
            if (matches) {
                results.add(rate);
            }
        }
        return ResponseEntity.ok(results);
    }

    // Processing logic for CurrencyRateJob
    private void processCurrencyRateJob(String technicalId, CurrencyRateJob job) {
        log.info("Processing CurrencyRateJob id {} with source {}", technicalId, job.getSource());
        // Step 1: Validate source and requestedAt already done in isValid()

        // Step 2: Fetch currency rates (simulate external API call)
        List<CurrencyRate> fetchedRates = fetchCurrencyRatesFromSource(job.getSource());

        // Step 3: For each fetched rate, create new immutable CurrencyRate entity
        for (CurrencyRate rate : fetchedRates) {
            String rateId = "rate-" + currencyRateIdCounter.getAndIncrement();
            rate.setJobId(technicalId);
            if (!rate.isValid()) {
                log.error("Invalid CurrencyRate data skipped: {}", rate);
                continue;
            }
            currencyRateCache.put(rateId, rate);
            log.info("Created CurrencyRate with id {} for job {}", rateId, technicalId);
            processCurrencyRate(rateId, rate);
        }

        // Step 4: Update job status to COMPLETED
        job.setStatus("COMPLETED");
        currencyRateJobCache.put(technicalId, job);
        log.info("Completed processing CurrencyRateJob id {}", technicalId);
    }

    // Processing logic for CurrencyRate
    private void processCurrencyRate(String technicalId, CurrencyRate rate) {
        log.info("Processing CurrencyRate id {}: {} to {}, rate {}", technicalId, rate.getCurrencyFrom(), rate.getCurrencyTo(), rate.getRate());
        // Validation already done in isValid()

        // Store or enrich data - for prototype, just log
        log.info("Stored CurrencyRate id {}", technicalId);

        // Mark processing done - no state change needed for prototype
    }

    // Simulated external API call to fetch currency rates from source
    private List<CurrencyRate> fetchCurrencyRatesFromSource(String source) {
        log.info("Fetching currency rates from source: {}", source);
        // For prototype, simulate static data
        List<CurrencyRate> rates = new ArrayList<>();
        CurrencyRate rate1 = new CurrencyRate();
        rate1.setCurrencyFrom("USD");
        rate1.setCurrencyTo("EUR");
        rate1.setRate(0.92f);
        rate1.setTimestamp(java.time.Instant.now().toString());
        rates.add(rate1);

        CurrencyRate rate2 = new CurrencyRate();
        rate2.setCurrencyFrom("USD");
        rate2.setCurrencyTo("JPY");
        rate2.setRate(134.5f);
        rate2.setTimestamp(java.time.Instant.now().toString());
        rates.add(rate2);

        return rates;
    }
}