```java
package com.java_template.prototype;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/prototype")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // --- Caches and counters ---
    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DigestData>> digestDataCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<EmailDispatch>> emailDispatchCache = new ConcurrentHashMap<>();

    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // --- POST /digest-requests ---
    @PostMapping("/digest-requests")
    public ResponseEntity<CreateResponse> createDigestRequest(@Valid @RequestBody DigestRequestCreateRequest request) {
        logger.info("Received new DigestRequest create request for email={}", request.getEmail());

        DigestRequest digestRequest = new DigestRequest();
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        digestRequest.setId(id);
        digestRequest.setEmail(request.getEmail());
        digestRequest.setMetadata(request.getMetadata() != null ? request.getMetadata() : Collections.emptyMap());
        digestRequest.setStatus(DigestRequest.Status.RECEIVED);
        digestRequest.setCreatedAt(Instant.now());
        digestRequest.setUpdatedAt(Instant.now());

        digestRequestCache.computeIfAbsent("digestRequests", k -> Collections.synchronizedList(new ArrayList<>())).add(digestRequest);

        // Trigger event processing
        processDigestRequest(digestRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateResponse(id, digestRequest.getStatus().name()));
    }

    // --- GET /digest-requests/{id} ---
    @GetMapping("/digest-requests/{id}")
    public ResponseEntity<DigestRequestResponse> getDigestRequest(@PathVariable String id) {
        DigestRequest digestRequest = findDigestRequestById(id);
        if (digestRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found with id " + id);
        }
        return ResponseEntity.ok(toDigestRequestResponse(digestRequest));
    }

    // --- GET /digest-requests/{id}/digest-data ---
    @GetMapping("/digest-requests/{id}/digest-data")
    public ResponseEntity<DigestDataResponse> getDigestDataByDigestRequestId(@PathVariable String id) {
        DigestData digestData = findDigestDataByDigestRequestId(id);
        if (digestData == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found for DigestRequest id " + id);
        }
        return ResponseEntity.ok(toDigestDataResponse(digestData));
    }

    // --- POST /email-dispatch/{id}/status ---
    @PostMapping("/email-dispatch/{id}/status")
    public ResponseEntity<EmailDispatchStatusResponse> updateEmailDispatchStatus(
            @PathVariable String id,
            @Valid @RequestBody EmailDispatchStatusUpdateRequest request) {

        EmailDispatch emailDispatch = findEmailDispatchById(id);
        if (emailDispatch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found with id " + id);
        }

        try {
            EmailDispatch.Status newStatus = EmailDispatch.Status.valueOf(request.getStatus());
            emailDispatch.setStatus(newStatus);
            emailDispatch.setSentAt(Instant.now());
            logger.info("EmailDispatch {} status updated to {}", id, newStatus);

            // Trigger event processing for EmailDispatch status update
            processEmailDispatchStatusUpdate(emailDispatch);

            return ResponseEntity.ok(new EmailDispatchStatusResponse(id, newStatus.name()));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value: " + request.getStatus());
        }
    }

    // --- GET /email-dispatch/{id} ---
    @GetMapping("/email-dispatch/{id}")
    public ResponseEntity<EmailDispatchResponse> getEmailDispatch(@PathVariable String id) {
        EmailDispatch emailDispatch = findEmailDispatchById(id);
        if (emailDispatch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found with id " + id);
        }
        return ResponseEntity.ok(toEmailDispatchResponse(emailDispatch));
    }

    // ***********************
    // Event-driven processing
    // ***********************

    private void processDigestRequest(DigestRequest digestRequest) {
        logger.info("Processing DigestRequest id={} email={}", digestRequest.getId(), digestRequest.getEmail());

        // Update status to PROCESSING
        digestRequest.setStatus(DigestRequest.Status.PROCESSING);
        digestRequest.setUpdatedAt(Instant.now());

        // Determine API endpoint & params - use metadata if present, else default
        String endpoint = "/v2/pet/findByStatus";
        String statusParam = "available";
        if (digestRequest.getMetadata() != null && digestRequest.getMetadata().containsKey("status")) {
            statusParam = digestRequest.getMetadata().get("status");
        }

        // Call external API - Petstore Swagger API
        String apiUrl = "https://petstore.swagger.io" + endpoint + "?status=" + statusParam;
        logger.info("Calling external API: {}", apiUrl);

        // TODO: In production, handle errors, retries, etc. For prototype, simple call.
        PetstorePet[] pets;
        try {
            pets = restTemplate.getForObject(apiUrl, PetstorePet[].class);
        } catch (Exception ex) {
            logger.error("Error calling external API for DigestRequest id={}: {}", digestRequest.getId(), ex.getMessage());
            digestRequest.setStatus(DigestRequest.Status.FAILED);
            digestRequest.setUpdatedAt(Instant.now());
            return; // stop processing on failure
        }

        // Save DigestData entity
        DigestData digestData = new DigestData();
        String digestDataId = String.valueOf(digestDataIdCounter.getAndIncrement());
        digestData.setId(digestDataId);
        digestData.setDigestRequestId(digestRequest.getId());
        digestData.setDataPayload(Arrays.toString(pets));
        digestData.setCreatedAt(Instant.now());

        digestDataCache.computeIfAbsent("digestData", k -> Collections.synchronizedList(new ArrayList<>())).add(digestData);

        logger.info("DigestData saved with id={} for DigestRequest id={}", digestDataId, digestRequest.getId());

        // Compose digest content (simple HTML)
        StringBuilder emailContent = new StringBuilder("<html><body><h3>Pet Digest</h3><ul>");
        if (pets != null) {
            for (PetstorePet pet : pets) {
                emailContent.append("<li>")
                        .append(pet.getName())
                        .append(" (Status: ").append(pet.getStatus()).append(")")
                        .append("</li>");
            }
        }
        emailContent.append("</ul></body></html>");

        // Create EmailDispatch entity
        EmailDispatch emailDispatch = new EmailDispatch();
        String emailDispatchId = String.valueOf(emailDispatchIdCounter.getAndIncrement());
        emailDispatch.setId(emailDispatchId);
        emailDispatch.setDigestRequestId(digestRequest.getId());
        emailDispatch.setEmailTo(digestRequest.getEmail());
        emailDispatch.setEmailContent(emailContent.toString());
        emailDispatch.setStatus(EmailDispatch.Status.PENDING);

        emailDispatchCache.computeIfAbsent("emailDispatches", k -> Collections.synchronizedList(new ArrayList<>())).add(emailDispatch);

        logger.info("EmailDispatch created with id={} for DigestRequest id={}", emailDispatchId, digestRequest.getId());

        // Simulate email sending by calling processEmailDispatch
        processEmailDispatch(emailDispatch);

        // Update DigestRequest status to COMPLETED (or FAILED if email sending failed)
        digestRequest.setStatus(emailDispatch.getStatus() == EmailDispatch.Status.SENT ?
                DigestRequest.Status.COMPLETED : DigestRequest.Status.FAILED);
        digestRequest.setUpdatedAt(Instant.now());
        logger.info("DigestRequest id={} processing finished with status={}", digestRequest.getId(), digestRequest.getStatus());
    }

    private void processEmailDispatch(EmailDispatch emailDispatch) {
        logger.info("Simulating email sending to {}", emailDispatch.getEmailTo());

        // TODO: Replace with real email sending logic. Here we simulate success.
        try {
            Thread.sleep(500); // simulate delay
        } catch (InterruptedException ignored) {}

        emailDispatch.setStatus(EmailDispatch.Status.SENT);
        emailDispatch.setSentAt(Instant.now());

        logger.info("Email sent successfully for EmailDispatch id={}", emailDispatch.getId());
    }

    private void processEmailDispatchStatusUpdate(EmailDispatch emailDispatch) {
        logger.info("Processing EmailDispatch status update for id={} status={}", emailDispatch.getId(), emailDispatch.getStatus());

        // Find related DigestRequest and update status accordingly
        DigestRequest digestRequest = findDigestRequestById(emailDispatch.getDigestRequestId());
        if (digestRequest == null) {
            logger.error("Related DigestRequest not found for EmailDispatch id={}", emailDispatch.getId());
            return;
        }

        if (emailDispatch.getStatus() == EmailDispatch.Status.SENT) {
            digestRequest.setStatus(DigestRequest.Status.COMPLETED);
        } else if (emailDispatch.getStatus() == EmailDispatch.Status.FAILED) {
            digestRequest.setStatus(DigestRequest.Status.FAILED);
        }
        digestRequest.setUpdatedAt(Instant.now());
        logger.info("DigestRequest id={} status updated to {}", digestRequest.getId(), digestRequest.getStatus());
    }

    // ***********************
    // Helper methods & DTOs
    // ***********************

    private DigestRequest findDigestRequestById(String id) {
        List<DigestRequest> list = digestRequestCache.get("digestRequests");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(d -> d.getId().equals(id)).findFirst().orElse(null);
        }
    }

    private DigestData findDigestDataByDigestRequestId(String digestRequestId) {
        List<DigestData> list = digestDataCache.get("digestData");
        if (list == null) return null;
        synchronized (list) {
            return list.stream()
                    .filter(d -> d.getDigestRequestId().equals(digestRequestId))
                    .findFirst()
                    .orElse(null);
        }
    }

    private EmailDispatch findEmailDispatchById(String id) {
        List<EmailDispatch> list = emailDispatchCache.get("emailDispatches");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(e -> e.getId().equals(id)).findFirst().orElse(null);
        }
    }

    private DigestRequestResponse toDigestRequestResponse(DigestRequest dr) {
        return new DigestRequestResponse(
                dr.getId(),
                dr.getEmail(),
                dr.getMetadata(),
                dr.getStatus().name(),
                dr.getCreatedAt(),
                dr.getUpdatedAt()
        );
    }

    private DigestDataResponse toDigestDataResponse(DigestData dd) {
        return new DigestDataResponse(dd.getDigestRequestId(), dd.getDataPayload());
    }

    private EmailDispatchResponse toEmailDispatchResponse(EmailDispatch ed) {
        return new EmailDispatchResponse(ed.getId(), ed.getEmailTo(), ed.getStatus().name(), ed.getSentAt());
    }


    // --- DTOs ---

    @Data
    public static class DigestRequestCreateRequest {
        @Email @NotBlank
        private String email;
        private Map<String, String> metadata;
    }

    @Data
    public static class CreateResponse {
        private final String id;
        private final String status;
    }

    @Data
    public static class DigestRequestResponse {
        private final String id;
        private final String email;
        private final Map<String, String> metadata;
        private final String status;
        private final Instant createdAt;
        private final Instant updatedAt;
    }

    @Data
    public static class DigestDataResponse {
        private final String digestRequestId;
        private final String dataPayload;
    }

    @Data
    public static class EmailDispatchStatusUpdateRequest {
        @NotBlank
        private String status; // PENDING, SENT, FAILED
    }

    @Data
    public static class EmailDispatchStatusResponse {
        private final String id;
        private final String status;
    }

    @Data
    public static class EmailDispatchResponse {
        private final String id;
        private final String emailTo;
        private final String status;
        private final Instant sentAt;
    }

    // --- Petstore API response DTO (simplified) ---
    @Data
    public static class PetstorePet {
        private Long id;
        private String name;
        private String status;
    }

    // --- Exception handler (basic) ---
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

}
```