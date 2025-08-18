package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/subscribers")
@Tag(name = "Subscriber")
public class SubscriberController {
    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Create or return existing subscriber (idempotent). Returns technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createSubscriber(@RequestBody SubscriberCreateRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            // build entity
            Subscriber data = new Subscriber();
            data.setId(UUID.randomUUID().toString());
            data.setEmail(request.getEmail());
            data.setName(request.getName());
            data.setConsent_given(request.getConsent_given());
            // default to pending_confirmation per workflow
            data.setStatus("pending_confirmation");

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("Execution error creating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error creating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Subscriber", description = "Retrieve subscriber by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<SubscriberResponse> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            SubscriberResponse resp = mapper.treeToValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            logger.error("Execution error getting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error getting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    static class SubscriberCreateRequest {
        @Schema(description = "Normalized email address", example = "alice@example.com")
        private String email;
        @Schema(description = "Display name", example = "Alice")
        private String name;
        @Schema(description = "Consent given flag", example = "true")
        private Boolean consent_given;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    static class SubscriberResponse {
        @Schema(description = "Technical ID of the subscriber")
        private String technicalId;
        @Schema(description = "Domain id of the subscriber")
        private String id;
        @Schema(description = "Normalized email")
        private String email;
        @Schema(description = "Display name")
        private String name;
        @Schema(description = "Subscriber status")
        private String status;
        @Schema(description = "Subscribed date (ISO)")
        private String subscribed_date;
        @Schema(description = "Consent given flag")
        private Boolean consent_given;
        @Schema(description = "Last interaction date (ISO)")
        private String last_interaction_date;
        @Schema(description = "Bounce count")
        private Integer bounce_count;
    }
}
