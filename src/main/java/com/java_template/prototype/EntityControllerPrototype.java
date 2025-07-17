package com.java_template.prototype;

import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.RetrievedData;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<RetrievedData>> retrievedDataCache = new ConcurrentHashMap<>();
    private final AtomicLong retrievedDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<DigestEmail>> digestEmailCache = new ConcurrentHashMap<>();
    private final AtomicLong digestEmailIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    public static class DigestRequestDTO {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Pattern(regexp = "available|pending|sold", message = "status must be one of 'available','pending','sold'")
        private String status;
    }

    @PostMapping("/digest-request")
    public Map<String, Object> addDigestRequest(@Valid @RequestBody DigestRequestDTO requestDto) {
        log.info("Received DigestRequest POST: email={}, status={}", requestDto.getEmail(), requestDto.getStatus());
        DigestRequest digestRequest = new DigestRequest();
        digestRequest.setEmail(requestDto.getEmail());
        digestRequest.setStatus("Accepted");
        digestRequest.setCreatedAt(new Date());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("status", requestDto.getStatus());
        digestRequest.setMetadata(metadata);

        String id = addDigestRequestEntity(digestRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("digestRequestId", id);
        response.put("status", "Accepted");
        return response;
    }

    @GetMapping("/digest-request/{id}")
    public Map<String, Object> getDigestRequest(@PathVariable @NotBlank String id) {
        DigestRequest dr = getDigestRequestEntity(id);
        if (dr == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest with id " + id + " not found");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("digestRequestId", dr.getId());
        response.put("email", dr.getEmail());
        response.put("status", dr.getStatus());

        DigestEmail email = getDigestEmailByDigestRequestId(id);
        response.put("digestContent", email != null && "Sent".equalsIgnoreCase(email.getStatus()) ? email.getContent() : null);
        return response;
    }

    private String addDigestRequestEntity(DigestRequest entity) {
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        entity.setId(id);
        digestRequestCache.computeIfAbsent("digestRequests", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);
        log.info("Saved DigestRequest entity with id {}", id);
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

    private String addRetrievedDataEntity(RetrievedData entity) {
        String id = String.valueOf(retrievedDataIdCounter.getAndIncrement());
        entity.setId(id);
        retrievedDataCache.computeIfAbsent("retrievedData", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);
        log.info("Saved RetrievedData entity with id {}", id);
        processRetrievedData(entity);
        return id;
    }

    private String addDigestEmailEntity(DigestEmail entity) {
        String id = String.valueOf(digestEmailIdCounter.getAndIncrement());
        entity.setId(id);
        digestEmailCache.computeIfAbsent("digestEmails", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);
        log.info("Saved DigestEmail entity with id {}", id);
        processDigestEmail(entity);
        return id;
    }

    private DigestEmail getDigestEmailByDigestRequestId(String digestRequestId) {
        List<DigestEmail> list = digestEmailCache.get("digestEmails");
        if (list == null) return null;
        synchronized (list) {
            return list.stream()
                .filter(e -> digestRequestId.equals(e.getDigestRequestId()) && "Sent".equalsIgnoreCase(e.getStatus()))
                .reduce((first, second) -> second).orElse(null);
        }
    }

    private void processDigestRequest(DigestRequest digestRequest) {
        log.info("Processing DigestRequest event for id {}", digestRequest.getId());
        String statusParam = "available";
        Map<String, Object> metadata = digestRequest.getMetadata();
        if (metadata != null && metadata.get("status") instanceof String) {
            statusParam = (String) metadata.get("status");
        }
        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            RetrievedData retrievedData = new RetrievedData();
            retrievedData.setDigestRequestId(digestRequest.getId());
            retrievedData.setDataPayload(response.getBody());
            retrievedData.setFetchedAt(new Date());
            addRetrievedDataEntity(retrievedData);

            digestRequest.setStatus("DataFetched");
            updateDigestRequestEntity(digestRequest);
        } catch (Exception e) {
            log.error("Error fetching data from external API: {}", e.getMessage(), e);
            digestRequest.setStatus("Failed");
            updateDigestRequestEntity(digestRequest);
        }
    }

    private void processRetrievedData(RetrievedData retrievedData) {
        log.info("Processing RetrievedData event for id {}", retrievedData.getId());
        String compiledContent = "<html><body><h3>Petstore Data Digest</h3><pre>" +
            escapeHtml(retrievedData.getDataPayload()) +
            "</pre></body></html>";
        DigestEmail digestEmail = new DigestEmail();
        digestEmail.setDigestRequestId(retrievedData.getDigestRequestId());
        digestEmail.setContent(compiledContent);
        digestEmail.setStatus("Ready");
        addDigestEmailEntity(digestEmail);

        DigestRequest dr = getDigestRequestEntity(retrievedData.getDigestRequestId());
        if (dr != null) {
            dr.setStatus("DigestCompiled");
            updateDigestRequestEntity(dr);
        }
    }

    private void processDigestEmail(DigestEmail digestEmail) {
        log.info("Processing DigestEmail event for id {}", digestEmail.getId());
        DigestRequest dr = getDigestRequestEntity(digestEmail.getDigestRequestId());
        if (dr == null) {
            log.error("DigestRequest not found for DigestEmail id {}", digestEmail.getId());
            return;
        }
        log.info("Mock sending email to {} with digest content length {}", dr.getEmail(), digestEmail.getContent().length());
        digestEmail.setStatus("Sent");
        digestEmail.setSentAt(new Date());
        updateDigestEmailEntity(digestEmail);
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

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }

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