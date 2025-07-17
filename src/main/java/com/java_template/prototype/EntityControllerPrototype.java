package com.java_template.prototype;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatchLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

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
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<DigestData>> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<EmailDispatchLog>> emailDispatchLogCache = new ConcurrentHashMap<>();
    private final AtomicLong emailDispatchLogIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/digest-requests")
    public ResponseEntity<CreateDigestResponse> createOrUpdateDigestRequest(@RequestBody @Valid DigestRequestInput input) {
        logger.info("Received DigestRequest create/update request for email={}", input.getEmail());

        DigestRequest req = new DigestRequest();
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        req.setId(id);
        req.setEmail(input.getEmail());
        req.setMetadata(Optional.ofNullable(input.getMetadata()).orElse(new HashMap<>()));
        req.setRequestedEndpoint(Optional.ofNullable(input.getRequestedEndpoint()).filter(s -> !s.isBlank()).orElse("/pet/findByStatus"));
        req.setRequestedParameters(Optional.ofNullable(input.getRequestedParameters()).orElse(Map.of("status", "available")));
        req.setDigestFormat(Optional.ofNullable(input.getDigestFormat()).orElse(DigestRequest.DigestFormat.HTML));
        req.setStatus(DigestRequest.Status.RECEIVED);
        req.setCreatedAt(Instant.now());
        req.setUpdatedAt(Instant.now());

        digestRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(req);
        logger.info("Saved DigestRequest id={} to cache", id);

        processDigestRequest(req);

        return ResponseEntity.ok(new CreateDigestResponse(id, req.getStatus().toString()));
    }

    @GetMapping("/digest-requests/{id}")
    public ResponseEntity<DigestRequest> getDigestRequest(@PathVariable @NotBlank String id) {
        Optional<DigestRequest> found = findDigestRequestById(id);
        if (found.isEmpty()) {
            logger.error("DigestRequest id={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        return ResponseEntity.ok(found.get());
    }

    @GetMapping("/digest-requests/{id}/digest-data")
    public ResponseEntity<DigestDataResponse> getDigestData(@PathVariable @NotBlank String id) {
        Optional<DigestData> found = findDigestDataByRequestId(id);
        if (found.isEmpty()) {
            logger.error("DigestData for DigestRequest id={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found");
        }
        return ResponseEntity.ok(new DigestDataResponse(found.get().getRetrievedData()));
    }

    private Optional<DigestRequest> findDigestRequestById(String id) {
        List<DigestRequest> list = digestRequestCache.getOrDefault("entities", Collections.emptyList());
        return list.stream().filter(r -> id.equals(r.getId())).findFirst();
    }

    private Optional<DigestData> findDigestDataByRequestId(String digestRequestId) {
        List<DigestData> list = digestDataCache.getOrDefault("entities", Collections.emptyList());
        return list.stream().filter(d -> digestRequestId.equals(d.getDigestRequestId())).findFirst();
    }

    private void processDigestRequest(DigestRequest req) {
        logger.info("Start processing DigestRequest id={}", req.getId());
        try {
            req.setStatus(DigestRequest.Status.PROCESSING);
            req.setUpdatedAt(Instant.now());

            String url = "https://petstore.swagger.io/v2" + req.getRequestedEndpoint();
            StringBuilder urlBuilder = new StringBuilder(url);
            if (req.getRequestedParameters() != null && !req.getRequestedParameters().isEmpty()) {
                urlBuilder.append("?");
                req.getRequestedParameters().forEach((k, v) -> urlBuilder.append(k).append("=").append(v).append("&"));
                urlBuilder.setLength(urlBuilder.length() - 1);
            }

            logger.info("Fetching data from external API: {}", urlBuilder);
            ResponseEntity<String> response = restTemplate.getForEntity(urlBuilder.toString(), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("External API call failed with status " + response.getStatusCodeValue());
            }
            String responseBody = response.getBody();

            DigestData digestData = new DigestData();
            String digestDataId = String.valueOf(digestDataIdCounter.getAndIncrement());
            digestData.setId(digestDataId);
            digestData.setDigestRequestId(req.getId());
            digestData.setRetrievedData(responseBody);
            digestData.setCreatedAt(Instant.now());
            digestDataCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(digestData);
            logger.info("Saved DigestData id={} for DigestRequest id={}", digestDataId, req.getId());

            String compiledDigest = compileDigest(responseBody, req.getDigestFormat());

            EmailDispatchLog emailLog = new EmailDispatchLog();
            String emailLogId = String.valueOf(emailDispatchLogIdCounter.getAndIncrement());
            emailLog.setId(emailLogId);
            emailLog.setDigestRequestId(req.getId());
            emailLog.setEmail(req.getEmail());
            emailLog.setDispatchStatus(EmailDispatchLog.DispatchStatus.PENDING);
            emailDispatchLogCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(emailLog);

            boolean emailSent = mockSendEmail(req.getEmail(), compiledDigest, req.getDigestFormat());
            if (emailSent) {
                emailLog.setDispatchStatus(EmailDispatchLog.DispatchStatus.SUCCESS);
                emailLog.setSentAt(Instant.now());
                req.setStatus(DigestRequest.Status.SENT);
                logger.info("Email sent successfully to {}", req.getEmail());
            } else {
                emailLog.setDispatchStatus(EmailDispatchLog.DispatchStatus.FAILED);
                emailLog.setSentAt(Instant.now());
                emailLog.setErrorMessage("Mock email sending failure");
                req.setStatus(DigestRequest.Status.FAILED);
                logger.error("Email sending failed for {}", req.getEmail());
            }

            req.setUpdatedAt(Instant.now());
        } catch (Exception e) {
            logger.error("Error processing DigestRequest id={}: {}", req.getId(), e.getMessage(), e);
            req.setStatus(DigestRequest.Status.FAILED);
            req.setUpdatedAt(Instant.now());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process DigestRequest: " + e.getMessage());
        }
    }

    private String compileDigest(String data, DigestRequest.DigestFormat format) {
        switch (format) {
            case HTML:
                return "<html><body><pre>" + escapeHtml(data) + "</pre></body></html>";
            case PLAIN_TEXT:
                return data;
            case ATTACHMENT:
                return Base64.getEncoder().encodeToString(data.getBytes());
            default:
                return data;
        }
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private boolean mockSendEmail(String toEmail, String content, DigestRequest.DigestFormat format) {
        logger.info("Mock sending email to {} with digest format {}", toEmail, format);
        // TODO: Replace with real email sending implementation
        return true;
    }

    @Data
    public static class DigestRequestInput {
        @NotBlank
        @Email
        private String email;
        private Map<String, String> metadata;
        private String requestedEndpoint;
        private Map<String, String> requestedParameters;
        private DigestRequest.DigestFormat digestFormat;
    }

    @Data
    public static class CreateDigestResponse {
        @NotNull
        private final String id;
        @NotNull
        private final String status;
    }

    @Data
    public static class DigestDataResponse {
        @NotNull
        private final String retrievedData;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }
}