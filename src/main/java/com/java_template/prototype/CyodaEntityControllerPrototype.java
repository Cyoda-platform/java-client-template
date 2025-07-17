package com.java_template.prototype;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/prototype/digest")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_DIGEST_REQUEST = "DigestRequest";
    private static final String ENTITY_DIGEST_DATA = "DigestData";
    private static final String ENTITY_EMAIL_DISPATCH = "EmailDispatch";

    @PostMapping("/requests") // must be first
    public ResponseEntity<CreateDigestResponse> createOrUpdateDigestRequest(
            @RequestBody @Valid DigestRequestInput input) throws ExecutionException, InterruptedException {

        DigestRequest dr = new DigestRequest();
        dr.setEmail(input.getEmail());
        dr.setMetadata(input.getMetadata() == null ? "" : input.getMetadata());
        dr.setStatus(DigestRequest.Status.RECEIVED);
        dr.setCreatedAt(Instant.now());

        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_DIGEST_REQUEST, ENTITY_VERSION, dr);
        UUID technicalId = idFuture.get();
        dr.setTechnicalId(technicalId);

        logger.info("Created DigestRequest technicalId={}", technicalId);
        processDigestRequest(dr);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateDigestResponse(technicalId.toString(), dr.getStatus().name()));
    }

    @GetMapping("/requests/{id}") // must be first
    public ResponseEntity<DigestRequestDetailsResponse> getDigestRequest(
            @PathVariable @NotBlank @Pattern(regexp = "[0-9a-fA-F\\-]{36}") String id) throws ExecutionException, InterruptedException {

        UUID technicalId = UUID.fromString(id);

        CompletableFuture<Object> drObjFuture = entityService.getItem(ENTITY_DIGEST_REQUEST, ENTITY_VERSION, technicalId);
        Object drObj = drObjFuture.get();
        if (drObj == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        DigestRequest dr = (DigestRequest) drObj;

        // Get DigestData by condition digestRequestId EQUALS dr.technicalId.toString()
        Condition condDigestData = Condition.of("$.digestRequestId", "EQUALS", technicalId.toString());
        SearchConditionRequest scDigestData = SearchConditionRequest.group("AND", condDigestData);
        CompletableFuture<Object> ddObjFuture = entityService.getItemsByCondition(ENTITY_DIGEST_DATA, ENTITY_VERSION, scDigestData)
                .thenApply(arrayNode -> {
                    if (arrayNode == null || arrayNode.size() == 0) return null;
                    try {
                        return entityService.getItem(ENTITY_DIGEST_DATA, ENTITY_VERSION,
                                UUID.fromString(arrayNode.get(0).get("technicalId").asText())).get().get();
                    } catch (Exception e) {
                        return null;
                    }
                });
        Object ddObj = ddObjFuture.get();
        DigestData dd = ddObj instanceof DigestData ? (DigestData) ddObj : null;

        // Get EmailDispatch by condition digestRequestId EQUALS dr.technicalId.toString()
        Condition condEmailDispatch = Condition.of("$.digestRequestId", "EQUALS", technicalId.toString());
        SearchConditionRequest scEmailDispatch = SearchConditionRequest.group("AND", condEmailDispatch);
        CompletableFuture<Object> edObjFuture = entityService.getItemsByCondition(ENTITY_EMAIL_DISPATCH, ENTITY_VERSION, scEmailDispatch)
                .thenApply(arrayNode -> {
                    if (arrayNode == null || arrayNode.size() == 0) return null;
                    try {
                        return entityService.getItem(ENTITY_EMAIL_DISPATCH, ENTITY_VERSION,
                                UUID.fromString(arrayNode.get(0).get("technicalId").asText())).get().get();
                    } catch (Exception e) {
                        return null;
                    }
                });
        Object edObj = edObjFuture.get();
        EmailDispatch ed = edObj instanceof EmailDispatch ? (EmailDispatch) edObj : null;

        DigestDataResponse ddr = dd == null ? null : new DigestDataResponse(dd.getFormat().name(), dd.getData());
        EmailDispatchResponse edr = ed == null ? null
                : new EmailDispatchResponse(ed.getStatus().name(), ed.getSentAt());

        DigestRequestDetailsResponse resp = new DigestRequestDetailsResponse(
                dr.getTechnicalId().toString(), dr.getEmail(), dr.getMetadata(), dr.getStatus().name(), ddr, edr);

        return ResponseEntity.ok(resp);
    }

    private void processDigestRequest(DigestRequest dr) {
        logger.info("Processing DigestRequest technicalId={}", dr.getTechnicalId());
        dr.setStatus(DigestRequest.Status.PROCESSING);
        try {
            entityService.updateItem(ENTITY_DIGEST_REQUEST, ENTITY_VERSION, dr.getTechnicalId(), dr).get();

            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            Pet[] pets = restTemplate.getForObject(url, Pet[].class);
            String dataJson = pets != null ? Arrays.toString(pets) : "[]";

            DigestData dd = new DigestData();
            dd.setDigestRequestId(dr.getTechnicalId().toString());
            dd.setData(dataJson);
            dd.setFormat(DigestData.Format.HTML);
            dd.setCreatedAt(Instant.now());

            UUID ddTechnicalId = entityService.addItem(ENTITY_DIGEST_DATA, ENTITY_VERSION, dd).get();
            dd.setTechnicalId(ddTechnicalId);

            EmailDispatch ed = new EmailDispatch();
            ed.setDigestRequestId(dr.getTechnicalId().toString());
            ed.setEmail(dr.getEmail());
            ed.setStatus(EmailDispatch.Status.PENDING);

            UUID edTechnicalId = entityService.addItem(ENTITY_EMAIL_DISPATCH, ENTITY_VERSION, ed).get();
            ed.setTechnicalId(edTechnicalId);

            simulateEmailSend(ed);

            dr.setStatus(DigestRequest.Status.COMPLETED);
            entityService.updateItem(ENTITY_DIGEST_REQUEST, ENTITY_VERSION, dr.getTechnicalId(), dr).get();

            logger.info("Completed DigestRequest technicalId={}", dr.getTechnicalId());
        } catch (Exception ex) {
            logger.error("Error processing DigestRequest technicalId={}", dr.getTechnicalId(), ex);
            try {
                dr.setStatus(DigestRequest.Status.FAILED);
                entityService.updateItem(ENTITY_DIGEST_REQUEST, ENTITY_VERSION, dr.getTechnicalId(), dr).get();
            } catch (Exception e) {
                logger.error("Failed to update DigestRequest status to FAILED for technicalId={}", dr.getTechnicalId(), e);
            }
        }
    }

    private void simulateEmailSend(EmailDispatch ed) {
        logger.info("Simulating email send to {}", ed.getEmail());
        try {
            Thread.sleep(500);
            ed.setStatus(EmailDispatch.Status.SENT);
            ed.setSentAt(Instant.now());
            entityService.updateItem(ENTITY_EMAIL_DISPATCH, ENTITY_VERSION, ed.getTechnicalId(), ed).get();
            logger.info("Email sent to {}", ed.getEmail());
        } catch (InterruptedException e) {
            logger.error("Email send interrupted", e);
            ed.setStatus(EmailDispatch.Status.FAILED);
            ed.setErrorMessage("Interrupted");
            try {
                entityService.updateItem(ENTITY_EMAIL_DISPATCH, ENTITY_VERSION, ed.getTechnicalId(), ed).get();
            } catch (Exception ex) {
                logger.error("Failed to update EmailDispatch status to FAILED for technicalId={}", ed.getTechnicalId(), ex);
            }
        } catch (Exception e) {
            logger.error("Failed to update EmailDispatch after send for technicalId={}", ed.getTechnicalId(), e);
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