package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/subscribers")
@Tag(name = "Subscriber", description = "Subscriber entity proxy API")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Creates a new Subscriber entity (triggers Subscriber workflow). Returns generated technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CreateSubscriberResponse> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
            @RequestBody CreateSubscriberRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if (request.getEmail() == null || request.getEmail().isBlank())
                throw new IllegalArgumentException("email is required");

            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(request.getEmail());
            subscriber.setName(request.getName());
            subscriber.setTimezone(request.getTimezone());
            // Minimal required fields to satisfy entity validation - set initial status and signup date.
            subscriber.setSignupDate(Instant.now().toString());
            subscriber.setStatus("CREATED");

            UUID id = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber
            ).get();

            CreateSubscriberResponse resp = new CreateSubscriberResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createSubscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in createSubscriber", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating subscriber", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createSubscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Subscriber", description = "Retrieves a Subscriber by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<GetSubscriberResponse> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID uuid = UUID.fromString(technicalId);

            ObjectNode node = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    uuid
            ).get();

            if (node == null) {
                return ResponseEntity.notFound().build();
            }

            GetSubscriberResponse resp = new GetSubscriberResponse();
            // technicalId from stored "id" field if present
            if (node.has("id") && !node.get("id").isNull()) {
                resp.setTechnicalId(node.get("id").asText());
            } else {
                resp.setTechnicalId(technicalId);
            }

            // email
            if (node.has("email") && !node.get("email").isNull()) resp.setEmail(node.get("email").asText());
            // name
            if (node.has("name") && !node.get("name").isNull()) resp.setName(node.get("name").asText());
            // signup_date: try both snake_case and camelCase
            if (node.has("signup_date") && !node.get("signup_date").isNull()) {
                resp.setSignupDate(node.get("signup_date").asText());
            } else if (node.has("signupDate") && !node.get("signupDate").isNull()) {
                resp.setSignupDate(node.get("signupDate").asText());
            }
            // status
            if (node.has("status") && !node.get("status").isNull()) resp.setStatus(node.get("status").asText());
            // timezone
            if (node.has("timezone") && !node.get("timezone").isNull()) resp.setTimezone(node.get("timezone").asText());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for getSubscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in getSubscriber", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting subscriber", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getSubscriber", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Payload to create a Subscriber")
    private static class CreateSubscriberRequest {
        @Schema(description = "Subscriber email address", example = "user@example.com", required = true)
        private String email;

        @Schema(description = "Optional display name", example = "Optional Name")
        private String name;

        @Schema(description = "Optional timezone", example = "UTC")
        private String timezone;
    }

    @Data
    @Schema(name = "CreateSubscriberResponse", description = "Response containing generated technicalId")
    private static class CreateSubscriberResponse {
        @Schema(description = "Generated technical id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "GetSubscriberResponse", description = "Subscriber representation returned by GET")
    private static class GetSubscriberResponse {
        @Schema(description = "Technical id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Subscriber email address", example = "user@example.com")
        private String email;

        @Schema(description = "Optional display name", example = "Optional Name")
        private String name;

        @Schema(description = "Signup date (ISO-8601)", example = "2025-08-26T12:00:00Z")
        private String signupDate;

        @Schema(description = "Subscriber status", example = "ACTIVE")
        private String status;

        @Schema(description = "Optional timezone", example = "UTC")
        private String timezone;
    }
}