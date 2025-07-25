package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
import java.time.Instant;
import java.util.regex.Pattern;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, DigestRequestJob> digestRequestJobCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, DigestData> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, DigestEmail> digestEmailCache = new ConcurrentHashMap<>();
    private final AtomicLong digestEmailIdCounter = new AtomicLong(1);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);

    @PostMapping("/digestRequestJob")
    public ResponseEntity<?> createDigestRequestJob(@RequestBody DigestRequestJob requestJob) {
        if (requestJob == null) {
            log.error("Received null DigestRequestJob");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is missing");
        }
        if (requestJob.getEmail() == null || requestJob.getEmail().isBlank()) {
            log.error("Email is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is required");
        }
        if (!EMAIL_PATTERN.matcher(requestJob.getEmail()).matches()) {
            log.error("Invalid email format: {}", requestJob.getEmail());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email format");
        }
        if (requestJob.getMetadata() == null || requestJob.getMetadata().isBlank()) {
            log.error("Metadata is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Metadata is required");
        }

        String technicalId = "job-" + digestRequestJobIdCounter.getAndIncrement();
        requestJob.setStatus("PENDING");
        requestJob.setCreatedAt(Instant.now());
        digestRequestJobCache.put(technicalId, requestJob);

        log.info("DigestRequestJob created with technicalId: {}", technicalId);

        processDigestRequestJob(technicalId, requestJob);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/digestRequestJob/{id}")
    public ResponseEntity<?> getDigestRequestJob(@PathVariable("id") String id) {
        DigestRequestJob job = digestRequestJobCache.get(id);
        if (job == null) {
            log.error("DigestRequestJob not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequestJob not found");
        }
        return ResponseEntity.ok(job);
    }

    @GetMapping("/digestData/{id}")
    public ResponseEntity<?> getDigestData(@PathVariable("id") String id) {
        DigestData data = digestDataCache.get(id);
        if (data == null) {
            log.error("DigestData not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestData not found");
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/digestEmail/{id}")
    public ResponseEntity<?> getDigestEmail(@PathVariable("id") String id) {
        DigestEmail email = digestEmailCache.get(id);
        if (email == null) {
            log.error("DigestEmail not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestEmail not found");
        }
        return ResponseEntity.ok(email);
    }

    // Processing DigestRequestJob - triggers data retrieval and next steps
    private void processDigestRequestJob(String technicalId, DigestRequestJob job) {
        log.info("Processing DigestRequestJob with ID: {}", technicalId);

        // Validate email and metadata already done in controller

        // Update status to PROCESSING
        job.setStatus("PROCESSING");
        digestRequestJobCache.put(technicalId, job);

        // Parse metadata JSON for endpoint and params
        String endpoint = "/pet/findByStatus"; // default
        Map<String,Object> params = new HashMap<>();
        try {
            Map<String,Object> metadataMap = objectMapper.readValue(job.getMetadata(), new TypeReference<Map<String,Object>>() {});
            if (metadataMap.containsKey("endpoint")) {
                endpoint = metadataMap.get("endpoint").toString();
            }
            if (metadataMap.containsKey("params")) {
                Object paramObj = metadataMap.get("params");
                if (paramObj instanceof Map) {
                    params = (Map<String,Object>) paramObj;
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse metadata JSON: {}", e.getMessage());
        }

        // Build query string for params
        StringBuilder queryBuilder = new StringBuilder();
        if (!params.isEmpty()) {
            queryBuilder.append("?");
            params.forEach((k,v) -> {
                queryBuilder.append(k).append("=").append(v.toString()).append("&");
            });
            queryBuilder.setLength(queryBuilder.length() - 1); // remove trailing &
        }

        String url = "https://petstore.swagger.io/v2" + endpoint + queryBuilder.toString();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String retrievedData = response.body();

                // Create DigestData entity
                DigestData digestData = new DigestData();
                String digestDataId = "data-" + digestDataIdCounter.getAndIncrement();
                digestData.setJobTechnicalId(technicalId);
                digestData.setRetrievedData(retrievedData);
                digestData.setFormat("HTML"); // defaulting to HTML for now
                digestData.setCreatedAt(Instant.now());
                digestDataCache.put(digestDataId, digestData);

                log.info("DigestData created with ID: {}", digestDataId);

                processDigestData(digestDataId, digestData);

            } else {
                log.error("External API call failed with status: {}", response.statusCode());
                job.setStatus("FAILED");
                digestRequestJobCache.put(technicalId, job);
            }
        } catch (Exception e) {
            log.error("Exception during external API call: {}", e.getMessage());
            job.setStatus("FAILED");
            digestRequestJobCache.put(technicalId, job);
        }
    }

    // Processing DigestData - compile data and create DigestEmail
    private void processDigestData(String digestDataId, DigestData data) {
        log.info("Processing DigestData with ID: {}", digestDataId);

        // Compile retrieved data into digest content
        String compiledContent = compileDigestContent(data.getRetrievedData(), data.getFormat());

        // Create DigestEmail entity
        DigestEmail digestEmail = new DigestEmail();
        String digestEmailId = "email-" + digestEmailIdCounter.getAndIncrement();
        digestEmail.setJobTechnicalId(data.getJobTechnicalId());
        DigestRequestJob job = digestRequestJobCache.get(data.getJobTechnicalId());
        if (job != null) {
            digestEmail.setEmail(job.getEmail());
        }
        digestEmail.setContent(compiledContent);
        digestEmail.setSentAt(null);
        digestEmail.setStatus("PENDING");
        digestEmailCache.put(digestEmailId, digestEmail);

        log.info("DigestEmail created with ID: {}", digestEmailId);

        processDigestEmail(digestEmailId, digestEmail);
    }

    private String compileDigestContent(String rawData, String format) {
        // For simplicity, if format is HTML, wrap raw data in basic HTML template
        if ("HTML".equalsIgnoreCase(format)) {
            return "<html><body><pre>" + rawData + "</pre></body></html>";
        } else {
            return rawData; // plain text fallback
        }
    }

    // Processing DigestEmail - send the email and update status
    private void processDigestEmail(String digestEmailId, DigestEmail email) {
        log.info("Processing DigestEmail with ID: {}", digestEmailId);

        try {
            // Simulate email sending - replace with real email sending logic in production
            log.info("Sending email to: {}", email.getEmail());
            // Simulated send success
            email.setStatus("SENT");
            email.setSentAt(Instant.now());
            digestEmailCache.put(digestEmailId, email);

            // Update related job status to COMPLETED
            DigestRequestJob job = digestRequestJobCache.get(email.getJobTechnicalId());
            if (job != null) {
                job.setStatus("COMPLETED");
                digestRequestJobCache.put(email.getJobTechnicalId(), job);
            }
            log.info("Email sent successfully to: {}", email.getEmail());
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
            email.setStatus("FAILED");
            digestEmailCache.put(digestEmailId, email);

            DigestRequestJob job = digestRequestJobCache.get(email.getJobTechnicalId());
            if (job != null) {
                job.setStatus("FAILED");
                digestRequestJobCache.put(email.getJobTechnicalId(), job);
            }
        }
    }
}