package com.java_template.controller;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequest;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@Validated
@RequestMapping(path = "/prototype/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/digest-requests", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateResponse createDigestRequest(@RequestBody @Valid DigestRequestCreateRequest request) {
        logger.info("Received digest request creation for email {}", request.getEmail());

        DigestRequest entity = new DigestRequest();
        entity.setEmail(request.getEmail());
        entity.setMetadata(request.getMetadata() != null ? request.getMetadata() : Collections.emptyMap());
        entity.setStatus("CREATED");
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        try {
            entityService.addItem("DigestRequest", "1", entity).get();
            logger.info("Saved DigestRequest for email {}", request.getEmail());
        } catch (Exception e) {
            logger.error("Failed to save DigestRequest: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save DigestRequest");
        }

        return new CreateResponse(entity.getId(), entity.getStatus());
    }

    @GetMapping(path = "/digest-requests/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestRequest getDigestRequest(@PathVariable @NotBlank String id) {
        Optional<DigestRequest> entity = entityService.getItem("DigestRequest", "1", id, DigestRequest.class);
        return entity.orElseThrow(() -> notFound("DigestRequest", id));
    }

    @DeleteMapping(path = "/digest-requests/{id}")
    public void deleteDigestRequest(@PathVariable @NotBlank String id) {
        try {
            entityService.deleteItem("DigestRequest", "1", id).get();
            logger.info("Deleted DigestRequest with id {}", id);
        } catch (Exception e) {
            logger.error("Failed to delete DigestRequest with id {}: {}", id, e.toString());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete DigestRequest");
        }
    }

    @GetMapping(path = "/digest-data/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestData getDigestData(@PathVariable @NotBlank String id) {
        Optional<DigestData> entity = entityService.getItem("DigestData", "1", id, DigestData.class);
        return entity.orElseThrow(() -> notFound("DigestData", id));
    }

    @GetMapping(path = "/digest-emails/{digestRequestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DigestEmail getDigestEmail(@PathVariable @NotBlank String digestRequestId) {
        List<DigestEmail> emails = entityService.getItemsByField("DigestEmail", "1", "digestRequestId", digestRequestId, DigestEmail.class);
        return emails.stream()
                .findFirst()
                .orElseThrow(() -> notFound("DigestEmail for DigestRequest", digestRequestId));
    }

    @PostMapping(path = "/digest-emails/{digestRequestId}/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public SendResponse sendDigestEmail(@PathVariable @NotBlank String digestRequestId) {
        DigestEmail email = getDigestEmail(digestRequestId);
        logger.info("Send triggered for DigestEmail id={} linked to DigestRequest id={}", email.getId(), digestRequestId);

        email.setSentStatus("SENT");
        email.setSentAt(Instant.now());

        try {
            entityService.updateItem("DigestEmail", "1", email.getId() != null ? java.util.UUID.fromString(email.getId()) : null, email).get();
            logger.info("DigestEmail sent successfully for DigestRequest id={}", digestRequestId);
        } catch (Exception e) {
            logger.error("Failed to update DigestEmail sent status: {}", e.toString());
        }

        return new SendResponse(email.getSentStatus());
    }

    private ResponseStatusException notFound(String entityName, String id) {
        String msg = entityName + " with id " + id + " not found";
        logger.error(msg);
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }

    public static class DigestRequestCreateRequest {
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is mandatory")
        private String email;
        private Map<String, Object> metadata;

        public String getEmail() {
            return email;
        }
        public void setEmail(String email) {
            this.email = email;
        }
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    public static class CreateResponse {
        private final String id;
        private final String status;

        public CreateResponse(String id, String status) {
            this.id = id;
            this.status = status;
        }

        public String getId() {
            return id;
        }
        public String getStatus() {
            return status;
        }
    }

    public static class SendResponse {
        private final String sentStatus;

        public SendResponse(String sentStatus) {
            this.sentStatus = sentStatus;
        }

        public String getSentStatus() {
            return sentStatus;
        }
    }
}