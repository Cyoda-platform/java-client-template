package com.java_template.application.controller.mail.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/mails")
@Tag(name = "Mail", description = "Mail entity proxy endpoints (version 1)")
public class MailController {

    private static final Logger logger = LoggerFactory.getLogger(MailController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MailController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Mail", description = "Create Mail entity. Triggers workflow processing. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createMail(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Mail creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateMailRequest.class)))
            @RequestBody CreateMailRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Basic validation (no business logic)
            if (request.getIsHappy() == null) {
                throw new IllegalArgumentException("isHappy is required");
            }
            if (request.getMailList() == null || request.getMailList().isEmpty()) {
                throw new IllegalArgumentException("mailList must contain at least one recipient");
            }
            for (String m : request.getMailList()) {
                if (m == null || m.isBlank()) {
                    throw new IllegalArgumentException("mailList must not contain blank addresses");
                }
            }

            Mail mail = new Mail();
            mail.setId(null); // creation, id not provided
            mail.setIsHappy(request.getIsHappy());
            mail.setMailList(request.getMailList());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    mail
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for createMail: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createMail execution: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createMail execution: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createMail", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error during createMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Create multiple Mails (batch)", description = "Create multiple Mail entities in batch. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createMailsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Batch mail creation payload", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateMailRequest.class))))
            @RequestBody List<CreateMailRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list must not be empty");
            }
            List<Mail> mails = requests.stream().map(req -> {
                Mail m = new Mail();
                m.setId(null);
                m.setIsHappy(req.getIsHappy());
                m.setMailList(req.getMailList());
                return m;
            }).collect(Collectors.toList());

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    mails
            );

            List<UUID> uuids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            resp.setTechnicalIds(uuids.stream().map(UUID::toString).collect(Collectors.toList()));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for createMailsBatch: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createMailsBatch execution: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createMailsBatch execution: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createMailsBatch", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createMailsBatch", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error during createMailsBatch", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Mail by technicalId", description = "Retrieve stored Mail processing result/status by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = MailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getMail(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    uuid
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(404).body("Entity not found");
            }

            // Convert ObjectNode to MailResponse DTO if possible
            MailResponse resp;
            try {
                resp = objectMapper.treeToValue(node, MailResponse.class);
            } catch (Exception e) {
                // Fallback: build MailResponse from available fields
                resp = new MailResponse();
                if (node.has("id")) {
                    resp.setTechnicalId(node.get("id").asText());
                } else {
                    resp.setTechnicalId(technicalId);
                }
                if (node.has("isHappy") && !node.get("isHappy").isNull()) {
                    resp.setIsHappy(node.get("isHappy").asBoolean());
                }
                if (node.has("mailList") && node.get("mailList").isArray()) {
                    resp.setMailList(objectMapper.convertValue(node.get("mailList"), List.class));
                }
                if (node.has("status")) {
                    resp.setStatus(node.get("status").asText());
                }
                if (node.has("lastUpdated")) {
                    resp.setLastUpdated(node.get("lastUpdated").asText());
                }
                if (node.has("notes")) {
                    resp.setNotes(node.get("notes").asText());
                }
            }

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for getMail: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during getMail execution: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during getMail execution: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getMail", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error during getMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "List all Mails", description = "Retrieve all Mail entities (raw stored representation).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = MailResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listMails() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION)
            );

            ArrayNode arrayNode = itemsFuture.get();
            if (arrayNode == null) {
                return ResponseEntity.ok().body(objectMapper.createArrayNode());
            }
            return ResponseEntity.ok(arrayNode);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during listMails execution: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during listMails", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during listMails", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error during listMails", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Find Mails by condition", description = "Retrieve Mail entities matching a simple search condition (in-memory filtering).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = MailResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchMails(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition for mails", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest conditionRequest) {
        try {
            if (conditionRequest == null) {
                throw new IllegalArgumentException("Search condition is required");
            }

            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    conditionRequest,
                    true
            );

            ArrayNode arrayNode = filteredFuture.get();
            if (arrayNode == null) {
                return ResponseEntity.ok().body(objectMapper.createArrayNode());
            }
            return ResponseEntity.ok(arrayNode);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid search condition for searchMails: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during searchMails execution: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during searchMails", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during searchMails", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error during searchMails", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Update Mail", description = "Update Mail entity to trigger workflow updates. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateMail(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateMailRequest.class)))
            @RequestBody UpdateMailRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID uuid = UUID.fromString(technicalId);

            ObjectNode payload = objectMapper.valueToTree(request);
            payload.put("id", technicalId);

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    uuid,
                    payload
            );

            UUID returnedId = updatedFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(returnedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for updateMail: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during updateMail execution: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during updateMail execution: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during updateMail", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during updateMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error during updateMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Delete Mail", description = "Delete Mail entity by technicalId. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteMail(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    uuid
            );

            UUID returnedId = deletedFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(returnedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for deleteMail: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during deleteMail execution: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during deleteMail execution: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during deleteMail", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during deleteMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error during deleteMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Retry Mail processing", description = "Request manual retry for a Mail entity (triggers retry workflow). Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(value = "/{technicalId}/retry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> retryMail(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);

            // Build minimal update payload to trigger retry in workflows.
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("id", technicalId);

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    uuid,
                    payload
            );

            UUID returnedId = updatedFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(returnedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for retryMail: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during retryMail execution: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during retryMail execution: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during retryMail", ex);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during retryMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception ex) {
            logger.error("Unexpected error during retryMail", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Static DTOs for requests/responses

    @Data
    @Schema(name = "CreateMailRequest", description = "Request to create a Mail entity")
    public static class CreateMailRequest {
        @Schema(description = "True for happy template, false for gloomy template", required = true, example = "true")
        private Boolean isHappy;

        @Schema(description = "List of recipient email addresses", required = true, example = "[\"alice@example.com\",\"bob@example.com\"]")
        private List<String> mailList;
    }

    @Data
    @Schema(name = "UpdateMailRequest", description = "Request to update a Mail entity")
    public static class UpdateMailRequest {
        @Schema(description = "True for happy template, false for gloomy template", example = "true")
        private Boolean isHappy;

        @Schema(description = "List of recipient email addresses", example = "[\"alice@example.com\",\"bob@example.com\"]")
        private List<String> mailList;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier (UUID)", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technicalIds")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical identifiers (UUIDs)", required = true)
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "MailResponse", description = "Mail retrieval response including processing status and metadata")
    public static class MailResponse {
        @Schema(description = "Technical identifier (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "True for happy template, false for gloomy template", example = "true")
        private Boolean isHappy;

        @Schema(description = "List of recipient email addresses", example = "[\"alice@example.com\",\"bob@example.com\"]")
        private List<String> mailList;

        @Schema(description = "Processing status (CREATED, EVALUATED, SENDING_HAPPY, SENDING_GLOOMY, SENT, FAILED, RETRY_REQUESTED)", example = "SENT")
        private String status;

        @Schema(description = "Last updated timestamp", example = "2025-08-26T12:00:00Z")
        private String lastUpdated;

        @Schema(description = "Notes describing processing outcome or errors", example = "All deliveries successful")
        private String notes;
    }
}