package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestRequest;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/digest-request")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    public static class DigestRequestDTO {
        @NotBlank(message = "userId must not be blank")
        private String userId;

        @NotBlank(message = "externalApiUrl must not be blank")
        @Pattern(regexp = "^https?://.+", message = "externalApiUrl must be a valid URL starting with http or https")
        private String externalApiUrl;

        @NotNull(message = "emailRecipients must not be null")
        @Size(min = 1, message = "emailRecipients must contain at least one email")
        private List<@Email(message = "emailRecipients must contain valid email addresses") String> emailRecipients;

        private String emailTemplateId; // optional

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getExternalApiUrl() { return externalApiUrl; }
        public void setExternalApiUrl(String externalApiUrl) { this.externalApiUrl = externalApiUrl; }

        public List<String> getEmailRecipients() { return emailRecipients; }
        public void setEmailRecipients(List<String> emailRecipients) { this.emailRecipients = emailRecipients; }

        public String getEmailTemplateId() { return emailTemplateId; }
        public void setEmailTemplateId(String emailTemplateId) { this.emailTemplateId = emailTemplateId; }
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> createDigestRequest(@RequestBody @Valid DigestRequestDTO dto) throws JsonProcessingException {
        DigestRequest request = new DigestRequest();
        request.setUserId(dto.getUserId());
        request.setExternalApiUrl(dto.getExternalApiUrl());
        request.setEmailRecipients(dto.getEmailRecipients());
        request.setEmailTemplateId(dto.getEmailTemplateId());
        request.setStatus(DigestRequest.StatusEnum.PENDING);
        LocalDateTime now = LocalDateTime.now();
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setRequestTime(now);

        // id must be set, as isValid checks id != null and not blank
        // generate business id as UUID string for example
        request.setId(UUID.randomUUID().toString());

        if (!request.isValid()) {
            logger.error("DigestRequest creation failed: validation errors");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Validation failed: required fields missing or invalid"));
        }

        return entityService.addItem("DigestRequest", ENTITY_VERSION, request)
                .thenApply(technicalId -> {
                    request.setTechnicalId(technicalId);
                    request.setId(technicalId.toString()); // store technicalId as string id
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", request.getId());
                    response.put("status", request.getStatus());
                    response.put("createdAt", request.getCreatedAt());
                    logger.info("DigestRequest created with technicalId: {}", technicalId);
                    return new ResponseEntity<>(response, HttpStatus.CREATED);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getDigestRequest(@PathVariable @NotBlank String id) throws JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequest not found"));
        }

        return entityService.getItem("DigestRequest", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("DigestRequest GET failed: no entity found with technicalId {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequest not found");
                    }
                    try {
                        DigestRequest request = objectMapper.treeToValue(objectNode, DigestRequest.class);
                        request.setTechnicalId(technicalId);
                        request.setId(id);
                        return ResponseEntity.ok(request);
                    } catch (JsonProcessingException e) {
                        logger.error("DigestRequest GET failed: deserialization error for technicalId {}: {}", id, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to deserialize DigestRequest");
                    }
                });
    }

    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> updateDigestRequest(@PathVariable @NotBlank String id,
                                                                    @RequestBody @Valid DigestRequestDTO dto) throws JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequest not found"));
        }

        return entityService.getItem("DigestRequest", ENTITY_VERSION, technicalId)
                .thenCompose(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("DigestRequest update failed: no entity found with technicalId {}", id);
                        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequest not found"));
                    }
                    try {
                        DigestRequest existingRequest = objectMapper.treeToValue(objectNode, DigestRequest.class);
                        existingRequest.setTechnicalId(technicalId);
                        existingRequest.setId(id);
                        existingRequest.setUserId(dto.getUserId());
                        existingRequest.setExternalApiUrl(dto.getExternalApiUrl());
                        existingRequest.setEmailRecipients(dto.getEmailRecipients());
                        existingRequest.setEmailTemplateId(dto.getEmailTemplateId());
                        existingRequest.setUpdatedAt(LocalDateTime.now());
                        existingRequest.setStatus(DigestRequest.StatusEnum.PENDING); // Reset status to trigger processing

                        if (!existingRequest.isValid()) {
                            logger.error("DigestRequest update failed validation for technicalId {}", id);
                            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Validation failed: required fields missing or invalid"));
                        }

                        return entityService.updateItem("DigestRequest", ENTITY_VERSION, technicalId, existingRequest)
                                .thenApply(updatedId -> {
                                    logger.info("DigestRequest updated and processing triggered for technicalId: {}", updatedId);
                                    existingRequest.setTechnicalId(updatedId);
                                    existingRequest.setId(updatedId.toString());
                                    return ResponseEntity.ok(existingRequest);
                                });

                    } catch (JsonProcessingException e) {
                        logger.error("DigestRequest update failed: deserialization error for technicalId {}: {}", id, e.getMessage());
                        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to deserialize DigestRequest"));
                    }
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> deleteDigestRequest(@PathVariable @NotBlank String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequest not found"));
        }

        return entityService.deleteItem("DigestRequest", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    logger.info("DigestRequest deleted with technicalId: {}", deletedId);
                    return ResponseEntity.ok("DigestRequest deleted successfully");
                });
    }
}