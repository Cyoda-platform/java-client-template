package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
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
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.List;

@RestController
@RequestMapping("/subscribers")
@Tag(name = "Subscribers", description = "Operations related to Subscribers")
public class SubscriberController {
    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Register a subscriber and start verification workflow.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberRequest request) {
        try {
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getChannels() == null || request.getChannels().isEmpty()) {
                throw new IllegalArgumentException("channels are required");
            }

            Subscriber subscriber = new Subscriber();
            subscriber.setId(request.getId());
            subscriber.setName(request.getName());
            subscriber.setEmail(request.getEmail());
            subscriber.setWebhookUrl(request.getWebhookUrl());
            subscriber.setChannels(request.getChannels());
            subscriber.setActive(request.getActive());
            subscriber.setFilters(request.getFilters());
            subscriber.setRetryPolicy(request.getRetryPolicy());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber
            );

            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new IdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("ExecutionException in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber", description = "Retrieve a Subscriber by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Subscriber.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            ObjectNode item = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();

            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in getSubscriber: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("ExecutionException in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class SubscriberRequest {
        @Schema(description = "Business subscriber id (optional)")
        private String id;
        @Schema(description = "Subscriber name", required = true)
        private String name;
        @Schema(description = "Contact email (optional)")
        private String email;
        @Schema(description = "Webhook URL (optional)")
        private String webhookUrl;
        @Schema(description = "Preferred channels (EMAIL, WEBHOOK)")
        private List<String> channels;
        @Schema(description = "Whether subscriber is active")
        private Boolean active = true;
        @Schema(description = "Subscriber filters")
        private Map<String,Object> filters;
        @Schema(description = "Retry policy for delivery attempts")
        private Map<String,Object> retryPolicy;
    }

    @Data
    static class IdResponse {
        @Schema(description = "Technical ID assigned to the entity")
        private String technicalId;

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
