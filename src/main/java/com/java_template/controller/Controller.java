package com.java_template.controller;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/cyoda")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/digest-requests")
    public CompletableFuture<ResponseEntity<CreateResponse>> createDigestRequest(@RequestBody @Valid DigestRequestCreateRequest request) {
        logger.info("Received new DigestRequest create request for email={}", request.getEmail());

        DigestRequest digestRequest = new DigestRequest();
        digestRequest.setEmail(request.getEmail());
        digestRequest.setMetadata(request.getMetadata() != null ? request.getMetadata() : Collections.emptyMap());
        digestRequest.setStatus(DigestRequest.Status.RECEIVED);
        digestRequest.setCreatedAt(Instant.now());
        digestRequest.setUpdatedAt(Instant.now());

        // Persist the DigestRequest entity
        return entityService.addItem("DigestRequest", ENTITY_VERSION, digestRequest)
                .thenApply(id -> {
                    digestRequest.setId(id.toString());
                    // Business logic moved to workflow processors, so no processing here
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(new CreateResponse(id.toString(), digestRequest.getStatus().name()));
                });
    }

    @GetMapping("/digest-requests/{id}")
    public CompletableFuture<ResponseEntity<DigestRequestResponse>> getDigestRequest(@PathVariable String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("DigestRequest", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found with id " + id);
                    }
                    DigestRequest dr = JsonUtil.convert(objectNode, DigestRequest.class);
                    dr.setId(objectNode.get("technicalId").asText());
                    return ResponseEntity.ok(toDigestRequestResponse(dr));
                });
    }

    @GetMapping("/digest-requests/{id}/digest-data")
    public CompletableFuture<ResponseEntity<DigestDataResponse>> getDigestDataByDigestRequestId(@PathVariable String id) {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.digestRequestId", "EQUALS", id));
        return entityService.getItemsByCondition("DigestData", ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    if (arrayNode == null || arrayNode.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "DigestData not found for DigestRequest id " + id);
                    }
                    ObjectNode obj = (ObjectNode) arrayNode.get(0);
                    DigestData dd = JsonUtil.convert(obj, DigestData.class);
                    return ResponseEntity.ok(toDigestDataResponse(dd));
                });
    }

    @PostMapping("/email-dispatch/{id}/status")
    public CompletableFuture<ResponseEntity<EmailDispatchStatusResponse>> updateEmailDispatchStatus(
            @PathVariable String id,
            @RequestBody @Valid EmailDispatchStatusUpdateRequest request) {

        UUID technicalId = UUID.fromString(id);

        return entityService.getItem("EmailDispatch", ENTITY_VERSION, technicalId)
                .thenCompose(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "EmailDispatch not found with id " + id);
                    }
                    EmailDispatch emailDispatch = JsonUtil.convert(objectNode, EmailDispatch.class);
                    emailDispatch.setId(objectNode.get("technicalId").asText());

                    EmailDispatch.Status newStatus;
                    try {
                        newStatus = EmailDispatch.Status.valueOf(request.getStatus());
                    } catch (IllegalArgumentException ex) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Invalid status value: " + request.getStatus());
                    }
                    emailDispatch.setStatus(newStatus);
                    emailDispatch.setSentAt(Instant.now());

                    logger.info("EmailDispatch {} status updated to {}", id, newStatus);

                    // Business logic moved to workflow processors, so no processing here

                    return entityService.updateItem("EmailDispatch", ENTITY_VERSION, technicalId, emailDispatch)
                            .thenApply(updatedId -> ResponseEntity.ok(new EmailDispatchStatusResponse(id, newStatus.name())));
                });
    }

    @GetMapping("/email-dispatch/{id}")
    public CompletableFuture<ResponseEntity<EmailDispatchResponse>> getEmailDispatch(@PathVariable String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("EmailDispatch", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "EmailDispatch not found with id " + id);
                    }
                    EmailDispatch ed = JsonUtil.convert(objectNode, EmailDispatch.class);
                    ed.setId(objectNode.get("technicalId").asText());
                    return ResponseEntity.ok(toEmailDispatchResponse(ed));
                });
    }

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

    // Utility class for JSON conversions between ObjectNode and POJOs
    private static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T convert(ObjectNode node, Class<T> clazz) {
            try {
                return mapper.treeToValue(node, clazz);
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert ObjectNode to " + clazz.getSimpleName(), e);
            }
        }
    }

}