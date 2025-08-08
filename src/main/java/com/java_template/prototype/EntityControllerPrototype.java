package com.java_template.prototype;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Job> jobCache = new ConcurrentHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Laureate> laureateCache = new ConcurrentHashMap<>();
    private final AtomicLong laureateIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --------- JOB ENDPOINTS ---------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Map<String, String> request) {
        String jobName = request.get("jobName");
        if (jobName == null || jobName.isBlank()) {
            log.error("Job creation failed: jobName is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "job_" + jobIdCounter.getAndIncrement();
        Job job = new Job();
        job.setJobName(jobName);
        job.setStatus("SCHEDULED");
        job.setCreatedAt(OffsetDateTime.now());
        jobCache.put(technicalId, job);

        log.info("Job created with technicalId: {}", technicalId);
        try {
            processJob(technicalId, job);
        } catch (Exception e) {
            log.error("Error processing job {}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setErrorDetails(e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
        }
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<Job> getJob(@PathVariable String technicalId) {
        Job job = jobCache.get(technicalId);
        if (job == null) {
            log.error("Job not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // --------- LAUREATE ENDPOINTS ---------

    @GetMapping("/laureates/{technicalId}")
    public ResponseEntity<Laureate> getLaureate(@PathVariable String technicalId) {
        Laureate laureate = laureateCache.get(technicalId);
        if (laureate == null) {
            log.error("Laureate not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(laureate);
    }

    // --------- SUBSCRIBER ENDPOINTS ---------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriberRequest) {
        if (subscriberRequest.getSubscriberId() == null || subscriberRequest.getSubscriberId().isBlank()) {
            log.error("Subscriber creation failed: subscriberId is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = subscriberRequest.getSubscriberId();
        subscriberCache.put(technicalId, subscriberRequest);
        log.info("Subscriber created with technicalId: {}", technicalId);
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String technicalId) {
        Subscriber subscriber = subscriberCache.get(technicalId);
        if (subscriber == null) {
            log.error("Subscriber not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    // --------- PROCESS METHODS ---------

    private void processJob(String technicalId, Job job) {
        log.info("Processing job {}", technicalId);

        job.setStatus("INGESTING");
        jobCache.put(technicalId, job);

        try {
            // Fetch Nobel laureates data from OpenDataSoft API
            String apiUrl = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";
            String jsonResponse = fetchApiData(apiUrl);
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode records = rootNode.path("records");

            if (!records.isArray()) {
                throw new RuntimeException("Invalid data format: records array not found");
            }

            // Process each laureate record
            for (JsonNode record : records) {
                JsonNode fields = record.path("fields");
                Laureate laureate = new Laureate();

                // Map fields to Laureate entity fields
                laureate.setTechnicalId("laureate_" + laureateIdCounter.getAndIncrement());
                laureate.setLaureateId(fields.path("id").asInt());
                laureate.setFirstname(fields.path("firstname").asText(null));
                laureate.setSurname(fields.path("surname").asText(null));
                laureate.setBorn(parseLocalDate(fields.path("born").asText(null)));
                laureate.setDied(parseLocalDate(fields.path("died").asText(null)));
                laureate.setBorncountry(fields.path("borncountry").asText(null));
                laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
                laureate.setBorncity(fields.path("borncity").asText(null));
                laureate.setGender(fields.path("gender").asText(null));
                laureate.setYear(fields.path("year").asText(null));
                laureate.setCategory(fields.path("category").asText(null));
                laureate.setMotivation(fields.path("motivation").asText(null));
                laureate.setAffiliationName(fields.path("name").asText(null));
                laureate.setAffiliationCity(fields.path("city").asText(null));
                laureate.setAffiliationCountry(fields.path("country").asText(null));

                // Save laureate and process
                laureateCache.put(laureate.getTechnicalId(), laureate);
                processLaureate(laureate.getTechnicalId(), laureate);
            }

            job.setStatus("SUCCEEDED");
            job.setCompletedAt(OffsetDateTime.now());
            jobCache.put(technicalId, job);

            // Process notifications
            processNotification();

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            jobCache.put(technicalId, job);

            log.info("Job {} processed successfully", technicalId);
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorDetails(e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            jobCache.put(technicalId, job);
            log.error("Job {} failed: {}", technicalId, e.getMessage());
        }
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        log.info("Processing laureate {}", technicalId);

        // Validation processor
        if (!validateLaureate(laureate)) {
            log.error("Validation failed for laureate {}", technicalId);
            return;
        }

        // Enrichment processor: normalize country codes (to uppercase)
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase(Locale.ROOT));
        }

        // Example: Calculate age at award if possible
        if (laureate.getBorn() != null && laureate.getYear() != null) {
            try {
                int awardYear = Integer.parseInt(laureate.getYear());
                int birthYear = laureate.getBorn().getYear();
                int ageAtAward = awardYear - birthYear;
                log.info("Laureate {} age at award: {}", technicalId, ageAtAward);
            } catch (NumberFormatException ignored) {
            }
        }

        // Persist enriched data (already stored in cache)
        laureateCache.put(technicalId, laureate);
    }

    private void processNotification() {
        log.info("Processing notifications to active subscribers");
        for (Subscriber subscriber : subscriberCache.values()) {
            if (Boolean.TRUE.equals(subscriber.getActive())) {
                try {
                    sendNotification(subscriber);
                } catch (Exception e) {
                    log.error("Failed to send notification to subscriber {}: {}", subscriber.getSubscriberId(), e.getMessage());
                }
            }
        }
    }

    // --------- HELPER METHODS ---------

    private String fetchApiData(String apiUrl) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        int status = con.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("API request failed with status code: " + status);
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        con.disconnect();
        return content.toString();
    }

    private boolean validateLaureate(Laureate laureate) {
        if (laureate.getLaureateId() == null) return false;
        if (isBlank(laureate.getFirstname())) return false;
        if (isBlank(laureate.getSurname())) return false;
        if (isBlank(laureate.getYear())) return false;
        if (isBlank(laureate.getCategory())) return false;
        return true;
    }

    private void sendNotification(Subscriber subscriber) {
        // Example: simulate sending notification (e.g., email or webhook)
        log.info("Sending notification to subscriber {} at {}", subscriber.getSubscriberId(), subscriber.getContactAddress());
        // Real implementation would send email or HTTP request here
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
}