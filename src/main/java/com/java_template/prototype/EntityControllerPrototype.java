package com.java_template.prototype;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@Validated
@RequestMapping(path = "/prototype/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<DigestData>> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<DigestEmail>> digestEmailCache = new ConcurrentHashMap<>();
    private final AtomicLong digestEmailIdCounter = new AtomicLong(1);

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://petstore.swagger.io/v2")
            .build();

    @PostMapping(path = "/digest-requests", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateResponse createDigestRequest(@RequestBody @Valid DigestRequestCreateRequest request) {
        logger.info("Received digest request creation for email {}", request.getEmail());

        DigestRequest entity = new DigestRequest();
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        entity.setId(id);
        entity.setEmail(request.getEmail());
        entity.setMetadata(request.getMetadata() != null ? request.getMetadata() : Collections.emptyMap());
        entity.setStatus("CREATED");
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        digestRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);
        logger.info("Saved DigestRequest with id {}", id);

        processDigestRequest(entity);

        return new CreateResponse(id, entity.getStatus());
    }

    @GetMapping(path = "/digest-requests/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestRequest getDigestRequest(@PathVariable @NotBlank String id) {
        return getEntityById(digestRequestCache, id)
                .orElseThrow(() -> notFound("DigestRequest", id));
    }

    @DeleteMapping(path = "/digest-requests/{id}")
    public void deleteDigestRequest(@PathVariable @NotBlank String id) {
        deleteEntityById(digestRequestCache, id)
                .orElseThrow(() -> notFound("DigestRequest", id));
        logger.info("Deleted DigestRequest with id {}", id);
    }

    @GetMapping(path = "/digest-data/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestData getDigestData(@PathVariable @NotBlank String id) {
        return getEntityById(digestDataCache, id)
                .orElseThrow(() -> notFound("DigestData", id));
    }

    @GetMapping(path = "/digest-emails/{digestRequestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestEmail getDigestEmail(@PathVariable @NotBlank String digestRequestId) {
        List<DigestEmail> emails = digestEmailCache.getOrDefault("entities", Collections.emptyList());
        return emails.stream()
                .filter(e -> digestRequestId.equals(e.getDigestRequestId()))
                .findFirst()
                .orElseThrow(() -> notFound("DigestEmail for DigestRequest", digestRequestId));
    }

    @PostMapping(path = "/digest-emails/{digestRequestId}/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public SendResponse sendDigestEmail(@PathVariable @NotBlank String digestRequestId) {
        DigestEmail email = getDigestEmail(digestRequestId);
        logger.info("Send triggered for DigestEmail id={} linked to DigestRequest id={}", email.getId(), digestRequestId);

        email.setSentStatus("SENT");
        email.setSentAt(Instant.now());
        logger.info("DigestEmail sent successfully for DigestRequest id={}", digestRequestId);

        return new SendResponse(email.getSentStatus());
    }

    private void processDigestRequest(DigestRequest digestRequest) {
        logger.info("Processing DigestRequest event, id={}", digestRequest.getId());

        String endpoint = "/pet/findByStatus";
        Map<String, Object> params = new HashMap<>();
        if (digestRequest.getMetadata() != null) {
            Object ep = digestRequest.getMetadata().get("endpoint");
            if (ep instanceof String) endpoint = (String) ep;
            Object p = digestRequest.getMetadata().get("params");
            if (p instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castParams = (Map<String, Object>) p;
                params = castParams;
            }
        }

        try {
            List<Object> apiResponse = webClient.get()
                    .uri(uriBuilder -> {
                        var ub = uriBuilder.path(endpoint);
                        params.forEach(ub::queryParam);
                        return ub.build();
                    })
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            if (apiResponse == null) {
                logger.error("External API returned null response");
                digestRequest.setStatus("FAILED_DATA_RETRIEVAL");
                digestRequest.setUpdatedAt(Instant.now());
                return;
            }

            DigestData digestData = new DigestData();
            String dataId = String.valueOf(digestDataIdCounter.getAndIncrement());
            digestData.setId(dataId);
            digestData.setDigestRequestId(digestRequest.getId());
            digestData.setDataPayload(apiResponse);
            digestData.setRetrievedAt(Instant.now());

            digestDataCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(digestData);
            logger.info("Saved DigestData with id {}", dataId);

            digestRequest.setStatus("DATA_RETRIEVED");
            digestRequest.setUpdatedAt(Instant.now());
            processDigestData(digestData);

        } catch (Exception e) {
            logger.error("Error fetching external data for DigestRequest id={}: {}", digestRequest.getId(), e.toString());
            digestRequest.setStatus("FAILED_DATA_RETRIEVAL");
            digestRequest.setUpdatedAt(Instant.now());
        }
    }

    private void processDigestData(DigestData digestData) {
        logger.info("Processing DigestData event, id={}", digestData.getId());

        StringBuilder emailContent = new StringBuilder();
        emailContent.append("<html><body><h3>Your Digest Data</h3><pre>")
                .append(digestData.getDataPayload().toString())
                .append("</pre></body></html>");

        DigestEmail digestEmail = new DigestEmail();
        String emailId = String.valueOf(digestEmailIdCounter.getAndIncrement());
        digestEmail.setId(emailId);
        digestEmail.setDigestRequestId(digestData.getDigestRequestId());
        digestEmail.setEmailContent(emailContent.toString());
        digestEmail.setSentStatus("PENDING");

        digestEmailCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(digestEmail);
        logger.info("Saved DigestEmail with id {}", emailId);

        processDigestEmail(digestEmail);
    }

    private void processDigestEmail(DigestEmail digestEmail) {
        logger.info("Processing DigestEmail event, id={}", digestEmail.getId());
        digestEmail.setSentStatus("SENT");
        digestEmail.setSentAt(Instant.now());
        logger.info("DigestEmail id={} marked as SENT (prototype)", digestEmail.getId());
    }

    private <T> Optional<T> getEntityById(ConcurrentHashMap<String, List<T>> cache, String id) {
        List<T> entities = cache.getOrDefault("entities", Collections.emptyList());
        return entities.stream()
                .filter(e -> {
                    try {
                        var method = e.getClass().getMethod("getId");
                        Object val = method.invoke(e);
                        return id.equals(val);
                    } catch (Exception ex) {
                        logger.error("Reflection error in getEntityById: {}", ex.toString());
                        return false;
                    }
                })
                .findFirst();
    }

    private <T> Optional<T> deleteEntityById(ConcurrentHashMap<String, List<T>> cache, String id) {
        List<T> entities = cache.getOrDefault("entities", Collections.emptyList());
        synchronized (entities) {
            Iterator<T> it = entities.iterator();
            while (it.hasNext()) {
                T e = it.next();
                try {
                    var method = e.getClass().getMethod("getId");
                    Object val = method.invoke(e);
                    if (id.equals(val)) {
                        it.remove();
                        return Optional.of(e);
                    }
                } catch (Exception ex) {
                    logger.error("Reflection error in deleteEntityById: {}", ex.toString());
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private ResponseStatusException notFound(String entityName, String id) {
        String msg = entityName + " with id " + id + " not found";
        logger.error(msg);
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }

    @Data
    public static class DigestRequestCreateRequest {
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is mandatory")
        private String email;
        // Metadata map retained; nested validation not applied per instructions
        private Map<String, Object> metadata;
    }

    @Data
    public static class CreateResponse {
        private final String id;
        private final String status;
    }

    @Data
    public static class SendResponse {
        private final String sentStatus;
    }
}