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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@RequestMapping(path = "/prototype/digest")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // ==== Local caches and ID counters for each entity ====

    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<DigestData>> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<EmailDispatch>> emailDispatchCache = new ConcurrentHashMap<>();
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // ==== POST /prototype/digest/requests ====
    @PostMapping("/requests")
    public ResponseEntity<CreateDigestResponse> createOrUpdateDigestRequest(@Valid @RequestBody DigestRequestInput input) {
        try {
            DigestRequest digestRequest = new DigestRequest();
            String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
            digestRequest.setId(id);
            digestRequest.setEmail(input.getEmail());
            digestRequest.setMetadata(input.getMetadata() != null ? input.getMetadata() : Collections.emptyMap());
            digestRequest.setStatus(DigestRequest.Status.RECEIVED);
            digestRequest.setCreatedAt(Instant.now());

            addDigestRequest(digestRequest);

            logger.info("Created DigestRequest with id={}", id);

            // Event-driven processing simulation
            processDigestRequest(digestRequest);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CreateDigestResponse(id, digestRequest.getStatus().name()));
        } catch (Exception ex) {
            logger.error("Error creating DigestRequest", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create DigestRequest");
        }
    }

    // ==== GET /prototype/digest/requests/{id} ====
    @GetMapping("/requests/{id}")
    public ResponseEntity<DigestRequestDetailsResponse> getDigestRequest(@PathVariable("id") String id) {
        try {
            DigestRequest digestRequest = getDigestRequestById(id);
            if (digestRequest == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
            }

            // Retrieve related DigestData and EmailDispatch (assume one-to-one for simplicity)
            DigestData digestData = getDigestDataByRequestId(id);
            EmailDispatch emailDispatch = getEmailDispatchByRequestId(id);

            DigestDataResponse digestDataResponse = null;
            if (digestData != null) {
                digestDataResponse = new DigestDataResponse(digestData.getFormat().name(), digestData.getData());
            }

            EmailDispatchResponse emailDispatchResponse = null;
            if (emailDispatch != null) {
                emailDispatchResponse = new EmailDispatchResponse(
                        emailDispatch.getStatus().name(),
                        emailDispatch.getSentAt()
                );
            }

            DigestRequestDetailsResponse response = new DigestRequestDetailsResponse(
                    digestRequest.getId(),
                    digestRequest.getEmail(),
                    digestRequest.getMetadata(),
                    digestRequest.getStatus().name(),
                    digestDataResponse,
                    emailDispatchResponse
            );

            return ResponseEntity.ok(response);
        } catch (ResponseStatusException ex) {
            logger.error("Error fetching DigestRequest id={}: {}", id, ex.getStatusCode());
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error fetching DigestRequest id={}", id, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch DigestRequest");
        }
    }

    // ==== Cache operations ====

    private void addDigestRequest(DigestRequest dr) {
        digestRequestCache.computeIfAbsent("digestRequests", k -> Collections.synchronizedList(new ArrayList<>())).add(dr);
    }

    private DigestRequest getDigestRequestById(String id) {
        List<DigestRequest> list = digestRequestCache.get("digestRequests");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(dr -> dr.getId().equals(id)).findFirst().orElse(null);
        }
    }

    private void updateDigestRequest(DigestRequest updated) {
        List<DigestRequest> list = digestRequestCache.get("digestRequests");
        if (list == null) return;
        synchronized (list) {
            list.replaceAll(dr -> dr.getId().equals(updated.getId()) ? updated : dr);
        }
    }

    private void addDigestData(DigestData dd) {
        digestDataCache.computeIfAbsent("digestData", k -> Collections.synchronizedList(new ArrayList<>())).add(dd);
    }

    private DigestData getDigestDataByRequestId(String requestId) {
        List<DigestData> list = digestDataCache.get("digestData");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(dd -> dd.getDigestRequestId().equals(requestId)).findFirst().orElse(null);
        }
    }

    private void addEmailDispatch(EmailDispatch ed) {
        emailDispatchCache.computeIfAbsent("emailDispatches", k -> Collections.synchronizedList(new ArrayList<>())).add(ed);
    }

    private EmailDispatch getEmailDispatchByRequestId(String requestId) {
        List<EmailDispatch> list = emailDispatchCache.get("emailDispatches");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(ed -> ed.getDigestRequestId().equals(requestId)).findFirst().orElse(null);
        }
    }

    private void updateEmailDispatch(EmailDispatch updated) {
        List<EmailDispatch> list = emailDispatchCache.get("emailDispatches");
        if (list == null) return;
        synchronized (list) {
            list.replaceAll(ed -> ed.getId().equals(updated.getId()) ? updated : ed);
        }
    }

    // ==== Event-driven processing simulation ====

    private void processDigestRequest(DigestRequest digestRequest) {
        logger.info("Processing DigestRequest id={}", digestRequest.getId());
        // Update status to PROCESSING
        digestRequest.setStatus(DigestRequest.Status.PROCESSING);
        updateDigestRequest(digestRequest);

        // 1) Fetch data from external API (petstore.swagger.io)
        try {
            // TODO: You can customize endpoint/params according to metadata if needed
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            Pet[] pets = restTemplate.getForObject(url, Pet[].class);

            String dataJson = pets != null ? Arrays.toString(pets) : "[]";

            DigestData digestData = new DigestData();
            String ddId = String.valueOf(digestDataIdCounter.getAndIncrement());
            digestData.setId(ddId);
            digestData.setDigestRequestId(digestRequest.getId());
            digestData.setData(dataJson);
            digestData.setFormat(DigestData.Format.HTML); // default HTML
            digestData.setCreatedAt(Instant.now());

            addDigestData(digestData);

            // 2) Compile digest (for prototype, just use retrieved data as-is)

            // 3) Create EmailDispatch entity and send email asynchronously (simulate)
            EmailDispatch emailDispatch = new EmailDispatch();
            String edId = String.valueOf(emailDispatchIdCounter.getAndIncrement());
            emailDispatch.setId(edId);
            emailDispatch.setDigestRequestId(digestRequest.getId());
            emailDispatch.setEmail(digestRequest.getEmail());
            emailDispatch.setStatus(EmailDispatch.Status.PENDING);
            addEmailDispatch(emailDispatch);

            // Simulate email sending (async)
            simulateEmailSend(emailDispatch);

            // 4) Update DigestRequest status to COMPLETED
            digestRequest.setStatus(DigestRequest.Status.COMPLETED);
            updateDigestRequest(digestRequest);
            logger.info("Completed processing DigestRequest id={}", digestRequest.getId());

        } catch (Exception ex) {
            logger.error("Error processing DigestRequest id={}", digestRequest.getId(), ex);
            digestRequest.setStatus(DigestRequest.Status.FAILED);
            updateDigestRequest(digestRequest);
        }
    }

    // Simulate async email sending with immediate success for prototype
    private void simulateEmailSend(EmailDispatch emailDispatch) {
        logger.info("Simulating sending email to {}", emailDispatch.getEmail());
        try {
            // TODO: Replace with real email sending logic or async job
            Thread.sleep(500); // simulate delay

            emailDispatch.setStatus(EmailDispatch.Status.SENT);
            emailDispatch.setSentAt(Instant.now());
            updateEmailDispatch(emailDispatch);

            logger.info("Email sent successfully to {}", emailDispatch.getEmail());
        } catch (InterruptedException e) {
            logger.error("Email sending interrupted", e);
            emailDispatch.setStatus(EmailDispatch.Status.FAILED);
            emailDispatch.setErrorMessage("Interrupted");
            updateEmailDispatch(emailDispatch);
        }
    }


    // ==== Request/Response DTOs ====

    @Data
    public static class DigestRequestInput {
        @NotBlank
        @Email
        private String email;

        private Map<String, Object> metadata;
    }

    @Data
    public static class CreateDigestResponse {
        private final String id;
        private final String status;
    }

    @Data
    public static class DigestRequestDetailsResponse {
        private final String id;
        private final String email;
        private final Map<String, Object> metadata;
        private final String status;
        private final DigestDataResponse digestData;
        private final EmailDispatchResponse emailDispatch;
    }

    @Data
    public static class DigestDataResponse {
        private final String format;
        private final String data;
    }

    @Data
    public static class EmailDispatchResponse {
        private final String status;
        private final Instant sentAt;
    }

    // ==== External API Pet model (simplified) ====

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String status;
        // For prototype, only key fields are defined
    }

    // ==== Minimal exception handler ====

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return new ResponseEntity<>(errorBody, ex.getStatusCode());
    }

}
```