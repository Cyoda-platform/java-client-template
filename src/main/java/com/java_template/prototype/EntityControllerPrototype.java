package com.java_template.prototype;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/prototype")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DigestData>> digestDataCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<EmailDispatch>> emailDispatchCache = new ConcurrentHashMap<>();

    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/digest-requests") // must be first
    public ResponseEntity<CreateResponse> createDigestRequest(@RequestBody @Valid DigestRequestCreateRequest request) {
        logger.info("Received new DigestRequest create request for email={}", request.getEmail());

        DigestRequest digestRequest = new DigestRequest();
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        digestRequest.setId(id);
        digestRequest.setEmail(request.getEmail());
        digestRequest.setMetadata(request.getMetadata() != null ? request.getMetadata() : Collections.emptyMap());
        digestRequest.setStatus(DigestRequest.Status.RECEIVED);
        digestRequest.setCreatedAt(Instant.now());
        digestRequest.setUpdatedAt(Instant.now());

        digestRequestCache.computeIfAbsent("digestRequests", k -> Collections.synchronizedList(new ArrayList<>()))
                .add(digestRequest);

        processDigestRequest(digestRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateResponse(id, digestRequest.getStatus().name()));
    }

    @GetMapping("/digest-requests/{id}") // must be first
    public ResponseEntity<DigestRequestResponse> getDigestRequest(@PathVariable String id) {
        DigestRequest digestRequest = findDigestRequestById(id);
        if (digestRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found with id " + id);
        }
        return ResponseEntity.ok(toDigestRequestResponse(digestRequest));
    }

    @GetMapping("/digest-requests/{id}/digest-data") // must be first
    public ResponseEntity<DigestDataResponse> getDigestDataByDigestRequestId(@PathVariable String id) {
        DigestData digestData = findDigestDataByDigestRequestId(id);
        if (digestData == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "DigestData not found for DigestRequest id " + id);
        }
        return ResponseEntity.ok(toDigestDataResponse(digestData));
    }

    @PostMapping("/email-dispatch/{id}/status") // must be first
    public ResponseEntity<EmailDispatchStatusResponse> updateEmailDispatchStatus(
            @PathVariable String id,
            @RequestBody @Valid EmailDispatchStatusUpdateRequest request) {

        EmailDispatch emailDispatch = findEmailDispatchById(id);
        if (emailDispatch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "EmailDispatch not found with id " + id);
        }

        try {
            EmailDispatch.Status newStatus = EmailDispatch.Status.valueOf(request.getStatus());
            emailDispatch.setStatus(newStatus);
            emailDispatch.setSentAt(Instant.now());
            logger.info("EmailDispatch {} status updated to {}", id, newStatus);

            processEmailDispatchStatusUpdate(emailDispatch);

            return ResponseEntity.ok(new EmailDispatchStatusResponse(id, newStatus.name()));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status value: " + request.getStatus());
        }
    }

    @GetMapping("/email-dispatch/{id}") // must be first
    public ResponseEntity<EmailDispatchResponse> getEmailDispatch(@PathVariable String id) {
        EmailDispatch emailDispatch = findEmailDispatchById(id);
        if (emailDispatch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "EmailDispatch not found with id " + id);
        }
        return ResponseEntity.ok(toEmailDispatchResponse(emailDispatch));
    }

    private void processDigestRequest(DigestRequest digestRequest) {
        logger.info("Processing DigestRequest id={} email={}", digestRequest.getId(), digestRequest.getEmail());
        digestRequest.setStatus(DigestRequest.Status.PROCESSING);
        digestRequest.setUpdatedAt(Instant.now());

        String endpoint = "/v2/pet/findByStatus";
        String statusParam = "available";
        if (digestRequest.getMetadata().containsKey("status")) {
            statusParam = digestRequest.getMetadata().get("status");
        }

        String apiUrl = "https://petstore.swagger.io" + endpoint + "?status=" + statusParam;
        logger.info("Calling external API: {}", apiUrl);

        PetstorePet[] pets;
        try {
            pets = restTemplate.getForObject(apiUrl, PetstorePet[].class);
        } catch (Exception ex) {
            logger.error("Error calling external API for DigestRequest id={}: {}", digestRequest.getId(), ex.getMessage());
            digestRequest.setStatus(DigestRequest.Status.FAILED);
            digestRequest.setUpdatedAt(Instant.now());
            return;
        }

        DigestData digestData = new DigestData();
        String digestDataId = String.valueOf(digestDataIdCounter.getAndIncrement());
        digestData.setId(digestDataId);
        digestData.setDigestRequestId(digestRequest.getId());
        digestData.setDataPayload(Arrays.toString(pets));
        digestData.setCreatedAt(Instant.now());
        digestDataCache.computeIfAbsent("digestData", k -> Collections.synchronizedList(new ArrayList<>()))
                .add(digestData);

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

        EmailDispatch emailDispatch = new EmailDispatch();
        String emailDispatchId = String.valueOf(emailDispatchIdCounter.getAndIncrement());
        emailDispatch.setId(emailDispatchId);
        emailDispatch.setDigestRequestId(digestRequest.getId());
        emailDispatch.setEmailTo(digestRequest.getEmail());
        emailDispatch.setEmailContent(emailContent.toString());
        emailDispatch.setStatus(EmailDispatch.Status.PENDING);
        emailDispatchCache.computeIfAbsent("emailDispatches", k -> Collections.synchronizedList(new ArrayList<>()))
                .add(emailDispatch);

        processEmailDispatch(emailDispatch);

        digestRequest.setStatus(emailDispatch.getStatus() == EmailDispatch.Status.SENT
                ? DigestRequest.Status.COMPLETED : DigestRequest.Status.FAILED);
        digestRequest.setUpdatedAt(Instant.now());
        logger.info("DigestRequest id={} processing finished with status={}", digestRequest.getId(), digestRequest.getStatus());
    }

    private void processEmailDispatch(EmailDispatch emailDispatch) {
        logger.info("Simulating email sending to {}", emailDispatch.getEmailTo());
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        emailDispatch.setStatus(EmailDispatch.Status.SENT);
        emailDispatch.setSentAt(Instant.now());
        logger.info("Email sent successfully for EmailDispatch id={}", emailDispatch.getId());
    }

    private void processEmailDispatchStatusUpdate(EmailDispatch emailDispatch) {
        logger.info("Processing EmailDispatch status update for id={} status={}",
                emailDispatch.getId(), emailDispatch.getStatus());
        DigestRequest digestRequest = findDigestRequestById(emailDispatch.getDigestRequestId());
        if (digestRequest == null) {
            logger.error("Related DigestRequest not found for EmailDispatch id={}", emailDispatch.getId());
            return;
        }
        if (emailDispatch.getStatus() == EmailDispatch.Status.SENT) {
            digestRequest.setStatus(DigestRequest.Status.COMPLETED);
        } else {
            digestRequest.setStatus(DigestRequest.Status.FAILED);
        }
        digestRequest.setUpdatedAt(Instant.now());
        logger.info("DigestRequest id={} status updated to {}", digestRequest.getId(), digestRequest.getStatus());
    }

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
            return list.stream().filter(d -> d.getDigestRequestId().equals(digestRequestId)).findFirst().orElse(null);
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

    @Data
    public static class DigestRequestCreateRequest {
        @Email @NotBlank
        private String email;
        // metadata is optional, not validated (nested type)
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
        private String status;
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

    @Data
    public static class PetstorePet {
        private Long id;
        private String name;
        private String status;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handled ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}