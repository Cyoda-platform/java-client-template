package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody as OasRequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/mails")
@Tag(name = "Mail Controller")
public class MailController {

    private static final Logger logger = LoggerFactory.getLogger(MailController.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MailController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Mail (event)", description = "Persist a Mail which will start the Mail workflow. Returns generated technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createMail(@RequestBody MailCreateRequest request) {
        try {
            validateCreateRequest(request);

            Mail mail = new Mail();
            mail.setIsHappy(request.getIsHappy());
            mail.setMailList(request.getMailList());
            mail.setStatus("CREATED");
            mail.setAttemptCount(0);
            mail.setLastAttemptAt(null);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    mail
            );

            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(id.toString());
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create("/mails/" + id.toString()));
            return new ResponseEntity<>(resp, headers, HttpStatus.CREATED);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request to createMail: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createMail", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating mail", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Get Mail by technicalId", description = "Retrieve stored Mail object and current workflow state.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = MailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getMail(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<com.fasterxml.jackson.databind.node.ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            MailResponse resp = objectMapper.treeToValue(node, MailResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request to getMail: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getMail", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting mail", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Manual retry for a failed Mail", description = "Transition a FAILED Mail back to READY_TO_SEND to trigger retry. Requires operator privileges.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/{technicalId}/retry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> retryMail(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            String status = node.hasNonNull("status") ? node.get("status").asText() : null;
            if (status == null || !"FAILED".equals(status)) {
                throw new IllegalArgumentException("Only mails in FAILED state can be retried");
            }

            ObjectNode update = objectMapper.createObjectNode();
            update.put("status", "READY_TO_SEND");

            CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    uuid,
                    update
            );
            UUID updatedId = updateFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request to retryMail: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in retryMail", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrying mail", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in retryMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Cancel a Mail", description = "Manually cancel an in-flight or ready Mail. Transitions the Mail to FAILED state.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/{technicalId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cancelMail(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            String status = node.hasNonNull("status") ? node.get("status").asText() : null;
            if (status == null || "SENT".equals(status) || "FAILED".equals(status)) {
                throw new IllegalArgumentException("Only non-terminal mails can be cancelled");
            }

            ObjectNode update = objectMapper.createObjectNode();
            update.put("status", "FAILED");

            CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    uuid,
                    update
            );
            UUID updatedId = updateFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request to cancelMail: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in cancelMail", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while cancelling mail", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in cancelMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    private void validateCreateRequest(MailCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getMailList() == null || request.getMailList().isEmpty()) {
            throw new IllegalArgumentException("mailList must be present and non-empty");
        }
        for (String addr : request.getMailList()) {
            if (addr == null || addr.isBlank()) {
                throw new IllegalArgumentException("mailList contains blank address");
            }
            if (!EMAIL_PATTERN.matcher(addr).matches()) {
                throw new IllegalArgumentException("Invalid email address: " + addr);
            }
        }
    }

    @Data
    @Schema(name = "MailCreateRequest", description = "Request to create a Mail (event).")
    public static class MailCreateRequest {
        @Schema(description = "If provided, authoritative happiness evaluation.")
        private Boolean isHappy;

        @Schema(description = "Recipient email addresses", required = true)
        private List<String> mailList;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing generated technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Generated technicalId")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "MailResponse", description = "Mail entity with workflow state and metadata")
    public static class MailResponse {
        @Schema(description = "Technical ID of the Mail")
        private String technicalId;

        @Schema(description = "Determined happiness flag")
        private Boolean isHappy;

        @Schema(description = "Recipient email addresses")
        private List<String> mailList;

        @Schema(description = "Current workflow state")
        private String status;

        @Schema(description = "Number of send attempts")
        private Integer attemptCount;

        @Schema(description = "Timestamp of last attempt")
        private OffsetDateTime lastAttemptAt;

        @Schema(description = "When the Mail was created")
        private OffsetDateTime createdAt;

        @Schema(description = "When the Mail was last updated")
        private OffsetDateTime updatedAt;
    }
}
