package com.java_template.prototype;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
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

@Validated
@RestController
@RequestMapping(path = "/prototype/digest")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<DigestData>> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<EmailDispatch>> emailDispatchCache = new ConcurrentHashMap<>();
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/requests") // must be first
    public ResponseEntity<CreateDigestResponse> createOrUpdateDigestRequest(
            @RequestBody @Valid DigestRequestInput input) {
        try {
            DigestRequest dr = new DigestRequest();
            String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
            dr.setId(id);
            dr.setEmail(input.getEmail());
            dr.setMetadata(input.getMetadata() == null ? "" : input.getMetadata());
            dr.setStatus(DigestRequest.Status.RECEIVED);
            dr.setCreatedAt(Instant.now());
            addDigestRequest(dr);
            logger.info("Created DigestRequest id={}", id);
            processDigestRequest(dr);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CreateDigestResponse(id, dr.getStatus().name()));
        } catch (Exception ex) {
            logger.error("Error creating DigestRequest", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create DigestRequest");
        }
    }

    @GetMapping("/requests/{id}") // must be first
    public ResponseEntity<DigestRequestDetailsResponse> getDigestRequest(
            @PathVariable @NotBlank @Pattern(regexp="\\d+") String id) {
        try {
            DigestRequest dr = getDigestRequestById(id);
            if (dr == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
            }
            DigestData dd = getDigestDataByRequestId(id);
            EmailDispatch ed = getEmailDispatchByRequestId(id);
            DigestDataResponse ddr = dd == null ? null
                    : new DigestDataResponse(dd.getFormat().name(), dd.getData());
            EmailDispatchResponse edr = ed == null ? null
                    : new EmailDispatchResponse(ed.getStatus().name(), ed.getSentAt());
            DigestRequestDetailsResponse resp = new DigestRequestDetailsResponse(
                    dr.getId(), dr.getEmail(), dr.getMetadata(), dr.getStatus().name(), ddr, edr);
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException ex) {
            logger.error("Error fetching DigestRequest id={}: {}", id, ex.getStatusCode());
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error fetching DigestRequest id={}", id, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch DigestRequest");
        }
    }

    private void addDigestRequest(DigestRequest dr) {
        digestRequestCache.computeIfAbsent("digestRequests", k -> Collections.synchronizedList(new ArrayList<>()))
                .add(dr);
    }

    private DigestRequest getDigestRequestById(String id) {
        List<DigestRequest> list = digestRequestCache.get("digestRequests");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
        }
    }

    private void updateDigestRequest(DigestRequest dr) {
        List<DigestRequest> list = digestRequestCache.get("digestRequests");
        if (list == null) return;
        synchronized (list) {
            list.replaceAll(r -> r.getId().equals(dr.getId()) ? dr : r);
        }
    }

    private void addDigestData(DigestData dd) {
        digestDataCache.computeIfAbsent("digestData", k -> Collections.synchronizedList(new ArrayList<>()))
                .add(dd);
    }

    private DigestData getDigestDataByRequestId(String rid) {
        List<DigestData> list = digestDataCache.get("digestData");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(d -> d.getDigestRequestId().equals(rid)).findFirst().orElse(null);
        }
    }

    private void addEmailDispatch(EmailDispatch ed) {
        emailDispatchCache.computeIfAbsent("emailDispatches", k -> Collections.synchronizedList(new ArrayList<>()))
                .add(ed);
    }

    private EmailDispatch getEmailDispatchByRequestId(String rid) {
        List<EmailDispatch> list = emailDispatchCache.get("emailDispatches");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(e -> e.getDigestRequestId().equals(rid)).findFirst().orElse(null);
        }
    }

    private void updateEmailDispatch(EmailDispatch ed) {
        List<EmailDispatch> list = emailDispatchCache.get("emailDispatches");
        if (list == null) return;
        synchronized (list) {
            list.replaceAll(e -> e.getId().equals(ed.getId()) ? ed : e);
        }
    }

    private void processDigestRequest(DigestRequest dr) {
        logger.info("Processing DigestRequest id={}", dr.getId());
        dr.setStatus(DigestRequest.Status.PROCESSING);
        updateDigestRequest(dr);
        try {
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            Pet[] pets = restTemplate.getForObject(url, Pet[].class);
            String dataJson = pets != null ? Arrays.toString(pets) : "[]";
            DigestData dd = new DigestData();
            String ddId = String.valueOf(digestDataIdCounter.getAndIncrement());
            dd.setId(ddId);
            dd.setDigestRequestId(dr.getId());
            dd.setData(dataJson);
            dd.setFormat(DigestData.Format.HTML);
            dd.setCreatedAt(Instant.now());
            addDigestData(dd);
            EmailDispatch ed = new EmailDispatch();
            String edId = String.valueOf(emailDispatchIdCounter.getAndIncrement());
            ed.setId(edId);
            ed.setDigestRequestId(dr.getId());
            ed.setEmail(dr.getEmail());
            ed.setStatus(EmailDispatch.Status.PENDING);
            addEmailDispatch(ed);
            simulateEmailSend(ed);
            dr.setStatus(DigestRequest.Status.COMPLETED);
            updateDigestRequest(dr);
            logger.info("Completed DigestRequest id={}", dr.getId());
        } catch (Exception ex) {
            logger.error("Error processing DigestRequest id={}", dr.getId(), ex);
            dr.setStatus(DigestRequest.Status.FAILED);
            updateDigestRequest(dr);
        }
    }

    private void simulateEmailSend(EmailDispatch ed) {
        logger.info("Simulating email send to {}", ed.getEmail());
        try {
            Thread.sleep(500);
            ed.setStatus(EmailDispatch.Status.SENT);
            ed.setSentAt(Instant.now());
            updateEmailDispatch(ed);
            logger.info("Email sent to {}", ed.getEmail());
        } catch (InterruptedException e) {
            logger.error("Email send interrupted", e);
            ed.setStatus(EmailDispatch.Status.FAILED);
            ed.setErrorMessage("Interrupted");
            updateEmailDispatch(ed);
        }
    }

    @Data
    public static class DigestRequestInput {
        @NotBlank
        @Email
        private String email;
        @Size(max = 1000)
        private String metadata;
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
        private final String metadata;
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

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String status;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return new ResponseEntity<>(errorBody, ex.getStatusCode());
    }
}