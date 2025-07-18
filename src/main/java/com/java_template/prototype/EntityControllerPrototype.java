package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.DigestRequest;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, DigestRequest> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    // DTO for POST and PUT requests - primitives and String only, with validation annotations
    public static class DigestRequestDTO {
        @NotBlank(message = "userId must not be blank")
        private String userId;

        @NotBlank(message = "externalApiUrl must not be blank")
        @Pattern(regexp = "^https?://.+", message = "externalApiUrl must be a valid URL starting with http or https")
        private String externalApiUrl;

        @NotNull(message = "emailRecipients must not be null")
        @Size(min = 1, message = "emailRecipients must contain at least one email")
        private List<@Email(message = "emailRecipients must contain valid email addresses") String> emailRecipients;

        private String emailTemplateId; // optional, no constraints

        public String getUserId() {
            return userId;
        }
        public void setUserId(String userId) {
            this.userId = userId;
        }
        public String getExternalApiUrl() {
            return externalApiUrl;
        }
        public void setExternalApiUrl(String externalApiUrl) {
            this.externalApiUrl = externalApiUrl;
        }
        public List<String> getEmailRecipients() {
            return emailRecipients;
        }
        public void setEmailRecipients(List<String> emailRecipients) {
            this.emailRecipients = emailRecipients;
        }
        public String getEmailTemplateId() {
            return emailTemplateId;
        }
        public void setEmailTemplateId(String emailTemplateId) {
            this.emailTemplateId = emailTemplateId;
        }
    }

    @PostMapping("/digest-request")
    public ResponseEntity<?> createDigestRequest(@RequestBody @Valid DigestRequestDTO dto) {
        if (dto == null) {
            logger.error("DigestRequest creation failed: request body is null");
            return ResponseEntity.badRequest().body("Request body cannot be null");
        }

        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());

        DigestRequest request = new DigestRequest();
        request.setId(id);
        // technicalId is private UUID - assumed generated elsewhere or null here
        request.setUserId(dto.getUserId());
        request.setExternalApiUrl(dto.getExternalApiUrl());
        request.setEmailRecipients(dto.getEmailRecipients());
        request.setEmailTemplateId(dto.getEmailTemplateId());
        request.setStatus(DigestRequest.StatusEnum.PENDING);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setRequestTime(now);

        if (!request.isValid()) {
            logger.error("DigestRequest creation failed: validation errors for id {}", id);
            return ResponseEntity.badRequest().body("Validation failed: required fields missing or invalid");
        }

        digestRequestCache.put(id, request);
        processDigestRequest(request);

        Map<String, Object> response = new HashMap<>();
        response.put("id", request.getId());
        response.put("status", request.getStatus());
        response.put("createdAt", request.getCreatedAt());

        logger.info("DigestRequest created with ID: {}", id);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/digest-request/{id}")
    public ResponseEntity<?> getDigestRequest(
            @PathVariable @NotBlank(message = "id path variable must not be blank") String id) {
        DigestRequest request = digestRequestCache.get(id);
        if (request == null) {
            logger.error("DigestRequest GET failed: no entity found with ID {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequest not found");
        }
        return ResponseEntity.ok(request);
    }

    @PutMapping("/digest-request/{id}")
    public ResponseEntity<?> updateDigestRequest(
            @PathVariable @NotBlank(message = "id path variable must not be blank") String id,
            @RequestBody @Valid DigestRequestDTO dto) {
        DigestRequest existingRequest = digestRequestCache.get(id);
        if (existingRequest == null) {
            logger.error("DigestRequest update failed: no entity found with ID {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequest not found");
        }

        existingRequest.setUserId(dto.getUserId());
        existingRequest.setExternalApiUrl(dto.getExternalApiUrl());
        existingRequest.setEmailRecipients(dto.getEmailRecipients());
        existingRequest.setEmailTemplateId(dto.getEmailTemplateId());
        existingRequest.setUpdatedAt(java.time.LocalDateTime.now());
        existingRequest.setStatus(DigestRequest.StatusEnum.PENDING); // Reset status to trigger processing

        if (!existingRequest.isValid()) {
            logger.error("DigestRequest update failed validation for ID {}", id);
            return ResponseEntity.badRequest().body("Validation failed: required fields missing or invalid");
        }

        digestRequestCache.put(id, existingRequest);
        processDigestRequest(existingRequest);

        logger.info("DigestRequest updated and processing triggered for ID: {}", id);
        return ResponseEntity.ok(existingRequest);
    }

    @DeleteMapping("/digest-request/{id}")
    public ResponseEntity<?> deleteDigestRequest(
            @PathVariable @NotBlank(message = "id path variable must not be blank") String id) {
        DigestRequest removed = digestRequestCache.remove(id);
        if (removed == null) {
            logger.error("DigestRequest delete failed: no entity found with ID {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequest not found");
        }
        logger.info("DigestRequest deleted with ID: {}", id);
        return ResponseEntity.ok("DigestRequest deleted successfully");
    }

    private void processDigestRequest(DigestRequest entity) {
        logger.info("Processing DigestRequest with ID: {}", entity.getId());

        // TODO: Implement actual business logic here
        // Examples:
        // - Validate external API URL and email recipients again if needed
        // - Call external API to fetch data
        // - Format data and generate email content using template if provided
        // - Send email to recipients
        // - Update entity status to COMPLETED or FAILED accordingly
        // - Update updatedAt timestamp

        // For now, simulate processing by setting status to PROCESSING then COMPLETED
        entity.setStatus(DigestRequest.StatusEnum.PROCESSING);
        entity.setUpdatedAt(java.time.LocalDateTime.now());

        // Simulate success
        entity.setStatus(DigestRequest.StatusEnum.COMPLETED);
        entity.setUpdatedAt(java.time.LocalDateTime.now());

        digestRequestCache.put(entity.getId(), entity);
        logger.info("Completed processing DigestRequest with ID: {}", entity.getId());
    }
}