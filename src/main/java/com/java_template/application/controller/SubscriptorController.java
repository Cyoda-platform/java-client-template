package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriptor.version_1.Subscriptor;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/subscriptors")
@Tag(name = "Subscriptor")
public class SubscriptorController {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptorController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubscriptorController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Subscriptor", description = "Create a new Subscriptor record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriptor(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriptor create request") @RequestBody CreateSubscriptorRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("request body is required");
            if (request.getEmail() == null || request.getEmail().isBlank()) throw new IllegalArgumentException("email is required");

            Subscriptor s = new Subscriptor();
            s.setEmail(request.getEmail());
            s.setName(request.getName());
            s.setSubscribedAt(Instant.now());
            s.setTopics(request.getTopics() == null ? new java.util.ArrayList<>() : request.getTopics());
            s.setActive(true);
            s.setCreatedAt(Instant.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriptor.ENTITY_NAME,
                    String.valueOf(Subscriptor.ENTITY_VERSION),
                    s
            );
            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating Subscriptor", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Subscriptor", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating Subscriptor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriptor by email", description = "Retrieve a Subscriptor record by email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriptorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(path = "/email/{email}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getByEmail(@Parameter(name = "email", description = "Subscriber email") @PathVariable String email) {
        try {
            if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");

            CompletableFuture<java.util.concurrent.CompletableFuture> future = new CompletableFuture<>();
            // Search by email
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Subscriptor.ENTITY_NAME,
                    String.valueOf(Subscriptor.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.email", "EQUALS", email)),
                    true
            );
            com.fasterxml.jackson.databind.node.ArrayNode results = itemsFuture.get();
            if (results == null || results.size() == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriptor not found");
            }
            ObjectNode node = (ObjectNode) results.get(0);
            SubscriptorResponse resp = new SubscriptorResponse();
            if (node.hasNonNull("email")) resp.setEmail(node.get("email").asText());
            if (node.hasNonNull("name")) resp.setName(node.get("name").asText());
            if (node.hasNonNull("subscribedAt")) resp.setSubscribedAt(node.get("subscribedAt").asText());
            if (node.hasNonNull("topics")) resp.setTopics(node.get("topics").toString());
            if (node.hasNonNull("active")) resp.setActive(node.get("active").asBoolean());
            if (node.hasNonNull("createdAt")) resp.setCreatedAt(node.get("createdAt").asText());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching Subscriptor", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching Subscriptor", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while fetching Subscriptor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class CreateSubscriptorRequest {
        @Schema(description = "Subscriber email", required = true)
        private String email;
        @Schema(description = "Subscriber name")
        private String name;
        @Schema(description = "Topics to subscribe to")
        private java.util.List<String> topics;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id of the created entity")
        private String technicalId;
    }

    @Data
    static class SubscriptorResponse {
        @Schema(description = "Subscriber email")
        private String email;
        @Schema(description = "Subscriber name")
        private String name;
        @Schema(description = "Subscribed at timestamp")
        private String subscribedAt;
        @Schema(description = "Topics subscribed to (JSON array as string)")
        private String topics;
        @Schema(description = "Is subscription active")
        private Boolean active;
        @Schema(description = "Created at timestamp")
        private String createdAt;
    }
}
