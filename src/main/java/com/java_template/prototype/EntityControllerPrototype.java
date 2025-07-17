```java
package com.java_template.prototype;

import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.RetrievedData;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prototype controller for DigestRequest, RetrievedData, DigestEmail entities.
 * Implements event-driven style with ConcurrentHashMap caches and processEntity calls.
 * 
 * Base path: /prototype/
 */
@Slf4j
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    // ==== Caches and ID counters ====

    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<RetrievedData>> retrievedDataCache = new ConcurrentHashMap<>();
    private final AtomicLong retrievedDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<DigestEmail>> digestEmailCache = new ConcurrentHashMap<>();
    private final AtomicLong digestEmailIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // ==== DigestRequest DTO for POST request validation ====

    @Data
    private static class DigestRequestDTO {
        @NotBlank
        @Email
        private String email;
        private Map<String, Object> metadata = new HashMap<>();
    }

    // ==== POST /digest-request ====

    /**
     * Add a new DigestRequest entity, trigger event processing.
     */
    @PostMapping("/digest-request")
    public Map<String, Object> addDigestRequest(@Valid @RequestBody DigestRequestDTO requestDto) {
        log.info("Received DigestRequest POST: email={}, metadata={}", requestDto.getEmail(), requestDto.getMetadata());

        DigestRequest digestRequest = new DigestRequest();
        digestRequest.setEmail(requestDto.getEmail());
        digestRequest.setMetadata(requestDto.getMetadata());
        digestRequest.setStatus("Accepted");
        digestRequest.setCreatedAt(new Date());

        String id = addDigestRequestEntity(digestRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("digestRequestId", id);
        response.put("status", "Accepted");
        return response;
    }

    // ==== GET /digest-request/{id} ====

    /**
     * Retrieve the DigestRequest status and digest content if available.
     */
    @GetMapping("/digest-request/{id}")
    public Map<String, Object> getDigestRequest(@PathVariable String id) {
        DigestRequest dr = getDigestRequestEntity(id);
        if (dr == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest with id " + id + " not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("digestRequestId", dr.getId());
        response.put("email", dr.getEmail());
        response.put("status", dr.getStatus());

        // Find corresponding DigestEmail content (latest)
        DigestEmail email = getDigestEmailByDigestRequestId(id);
        if (email != null && "Sent".equalsIgnoreCase(email.getStatus())) {
            response.put("digestContent", email.getContent());
        } else {
            response.put("digestContent", null);
        }

        return response;
    }

    // =================== CACHE OPERATIONS & EVENT PROCESSING ====================

    // DigestRequest cache operations

    private String addDigestRequestEntity(DigestRequest entity) {
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        entity.setId(id);
        digestRequestCache.computeIfAbsent("digestRequests", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);
        log.info("Saved DigestRequest entity with id {}", id);

        // Trigger event processing
        processDigestRequest(entity);

        return id;
    }

    private DigestRequest getDigestRequestEntity(String id) {
        List<DigestRequest> list = digestRequestCache.get("digestRequests");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
        }
    }

    private void updateDigestRequestEntity(DigestRequest entity) {
        // For prototype, update by replacing existing entity with same id
        List<DigestRequest> list = digestRequestCache.get("digestRequests");
        if (list == null) return;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                if (entity.getId().equals(list.get(i).getId())) {
                    list.set(i, entity);
                    log.info("Updated DigestRequest entity id {}", entity.getId());
                    return;
                }
            }
        }
    }

    // RetrievedData cache operations

    private String addRetrievedDataEntity(RetrievedData entity) {
        String id = String.valueOf(retrievedDataIdCounter.getAndIncrement());
        entity.setId(id);
        retrievedDataCache.computeIfAbsent("retrievedData", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);
        log.info("Saved RetrievedData entity with id {}", id);

        // Trigger event processing
        processRetrievedData(entity);

        return id;
    }

    // DigestEmail cache operations

    private String addDigestEmailEntity(DigestEmail entity) {
        String id = String.valueOf(digestEmailIdCounter.getAndIncrement());
        entity.setId(id);
        digestEmailCache.computeIfAbsent("digestEmails", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);
        log.info("Saved DigestEmail entity with id {}", id);

        // Trigger event processing
        processDigestEmail(entity);

        return id;
    }

    private DigestEmail getDigestEmailByDigestRequestId(String digestRequestId) {
        List<DigestEmail> list = digestEmailCache.get("digestEmails");
        if (list == null) return null;
        synchronized (list) {
            // Return latest email with matching digestRequestId and status Sent
            return list.stream()
                    .filter(e -> digestRequestId.equals(e.getDigestRequestId()) && "Sent".equalsIgnoreCase(e.getStatus()))
                    .reduce((first, second) -> second)
                    .orElse(null);
        }
    }

    // =================== EVENT PROCESSING SIMULATION ====================

    /**
     * Process event after DigestRequest is saved.
     * 1) Extract metadata or use defaults
     * 2) Call external petstore API to retrieve data
     * 3) Save RetrievedData entity (triggers next event)
     */
    private void processDigestRequest(DigestRequest digestRequest) {
        log.info("Processing DigestRequest event for id {}", digestRequest.getId());

        // Extract parameters from metadata or use defaults
        Map<String, Object> metadata = digestRequest.getMetadata();
        String petEndpoint = "/v2/pet/findByStatus"; // Default endpoint
        String statusParam = "available";

        if (metadata != null && metadata.get("status") instanceof String) {
            statusParam = (String) metadata.get("status");
        }

        // Call external API https://petstore.swagger.io/v2/pet/findByStatus?status=...
        String url = "https://petstore.swagger.io" + petEndpoint + "?status=" + statusParam;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String responseBody = response.getBody();

            // Save RetrievedData entity with payload
            RetrievedData retrievedData = new RetrievedData();
            retrievedData.setDigestRequestId(digestRequest.getId());
            retrievedData.setDataPayload(responseBody);
            retrievedData.setFetchedAt(new Date());

            addRetrievedDataEntity(retrievedData);

            // Update DigestRequest status to "DataFetched"
            digestRequest.setStatus("DataFetched");
            updateDigestRequestEntity(digestRequest);

        } catch (Exception e) {
            log.error("Error fetching data from external API: {}", e.getMessage(), e);
            // Update DigestRequest status to "Failed"
            digestRequest.setStatus("Failed");
            updateDigestRequestEntity(digestRequest);
        }
    }

    /**
     * Process event after RetrievedData is saved.
     * 1) Compile digest content (HTML) using RetrievedData payload
     * 2) Save DigestEmail entity (triggers next event)
     */
    private void processRetrievedData(RetrievedData retrievedData) {
        log.info("Processing RetrievedData event for id {}", retrievedData.getId());

        // TODO: For prototype, compile simple HTML digest wrapping raw JSON payload
        String compiledContent = "<html><body><h3>Petstore Data Digest</h3><pre>" +
                escapeHtml(retrievedData.getDataPayload()) +
                "</pre></body></html>";

        DigestEmail digestEmail = new DigestEmail();
        digestEmail.setDigestRequestId(retrievedData.getDigestRequestId());
        digestEmail.setContent(compiledContent);
        digestEmail.setStatus("Ready");
        digestEmail.setSentAt(null);

        addDigestEmailEntity(digestEmail);

        // Update DigestRequest status to "DigestCompiled"
        DigestRequest dr = getDigestRequestEntity(retrievedData.getDigestRequestId());
        if (dr != null) {
            dr.setStatus("DigestCompiled");
            updateDigestRequestEntity(dr);
        }
    }

    /**
     * Process event after DigestEmail is saved.
     * 1) Send email to the user (mocked here)
     * 2) Update DigestEmail status to "Sent"
     */
    private void processDigestEmail(DigestEmail digestEmail) {
        log.info("Processing DigestEmail event for id {}", digestEmail.getId());

        // Get DigestRequest to find user email
        DigestRequest dr = getDigestRequestEntity(digestEmail.getDigestRequestId());
        if (dr == null) {
            log.error("DigestRequest not found for DigestEmail id {}", digestEmail.getId());
            return;
        }

        // TODO: Replace this mock with real email sending implementation
        log.info("Mock sending email to {} with digest content length {}", dr.getEmail(), digestEmail.getContent().length());

        // Update DigestEmail status and sentAt
        digestEmail.setStatus("Sent");
        digestEmail.setSentAt(new Date());

        // Update in cache
        updateDigestEmailEntity(digestEmail);

        // Update DigestRequest status to "Completed"
        dr.setStatus("Completed");
        updateDigestRequestEntity(dr);
    }

    private void updateDigestEmailEntity(DigestEmail entity) {
        List<DigestEmail> list = digestEmailCache.get("digestEmails");
        if (list == null) return;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                if (entity.getId().equals(list.get(i).getId())) {
                    list.set(i, entity);
                    log.info("Updated DigestEmail entity id {}", entity.getId());
                    return;
                }
            }
        }
    }

    // =================== UTILITIES ====================

    /**
     * Simple HTML escape utility for prototype safety.
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    // =================== Exception Handling ====================

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception ex) {
        log.error("Internal server error: {}", ex.getMessage(), ex);
        Map<String, Object> error = new HashMap<>();
        error.put("error", "500 INTERNAL_SERVER_ERROR");
        error.put("message", "Internal server error");
        return error;
    }
}
```