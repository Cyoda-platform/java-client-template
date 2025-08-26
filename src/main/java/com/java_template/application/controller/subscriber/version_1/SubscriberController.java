package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/subscribers")
@Tag(name = "Subscriber Controller", description = "Controller proxy for Subscriber entity operations (version 1)")
public class SubscriberController {
    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Register a new Subscriber. Returns technicalId of created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Subscriber creation request",
            required = true,
            content = @Content(schema = @Schema(implementation = SubscriberRequest.class),
                    examples = @ExampleObject(value = "{\n  \"subscriberId\": \"sub_42\",\n  \"name\": \"Research Team\",\n  \"contactEndpoint\": \"https://hooks.example.com/notify\",\n  \"filters\": { \"category\": \"physics\" },\n  \"format\": \"summary\"\n}"))
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing");
            }

            Subscriber entity = new Subscriber();
            entity.setSubscriberId(request.getSubscriberId());
            entity.setName(request.getName());
            entity.setContactEndpoint(request.getContactEndpoint());
            entity.setFormat(request.getFormat());
            entity.setCreatedAt(request.getCreatedAt());
            entity.setStatus(request.getStatus());

            if (request.getFilters() != null) {
                Subscriber.Filters filters = new Subscriber.Filters();
                filters.setCategory(request.getFilters().getCategory());
                filters.setCountry(request.getFilters().getCountry());
                filters.setPrizeYear(request.getFilters().getPrizeYear());
                entity.setFilters(filters);
            }

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );

            java.util.UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request when creating subscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Not found during create operation: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request during create operation: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating subscriber", e);
                return ResponseEntity.status(500).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalIdStr) {
        try {
            if (technicalIdStr == null || technicalIdStr.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID technicalId = UUID.fromString(technicalIdStr);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    technicalId
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(404).body("Subscriber not found");
            }

            // Convert to domain entity/object and then to response DTO
            Subscriber entity = mapper.treeToValue((JsonNode) node, Subscriber.class);

            SubscriberResponse resp = new SubscriberResponse();
            resp.setSubscriberId(entity.getSubscriberId());
            resp.setName(entity.getName());
            resp.setContactEndpoint(entity.getContactEndpoint());
            resp.setFormat(entity.getFormat());
            resp.setStatus(entity.getStatus());
            resp.setCreatedAt(entity.getCreatedAt());

            if (entity.getFilters() != null) {
                SubscriberResponse.FiltersResponse fr = new SubscriberResponse.FiltersResponse();
                fr.setCategory(entity.getFilters().getCategory());
                fr.setCountry(entity.getFilters().getCountry());
                fr.setPrizeYear(entity.getFilters().getPrizeYear());
                resp.setFilters(fr);
            }

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request when retrieving subscriber: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Subscriber not found: {}", cause.getMessage());
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Bad request during retrieval: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving subscriber", e);
                return ResponseEntity.status(500).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when retrieving subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Static DTOs for request/response

    @Data
    @Schema(name = "SubscriberRequest", description = "Payload to create a Subscriber")
    public static class SubscriberRequest {
        @Schema(description = "Business identifier for the subscriber", example = "sub_42")
        private String subscriberId;

        @Schema(description = "Display name", example = "Research Team")
        private String name;

        @Schema(description = "Contact endpoint (email or webhook URL)", example = "https://hooks.example.com/notify")
        private String contactEndpoint;

        @Schema(description = "Filter criteria", implementation = SubscriberRequest.FiltersRequest.class)
        private FiltersRequest filters;

        @Schema(description = "Format of notifications", example = "summary")
        private String format;

        @Schema(description = "Creation timestamp (ISO-8601)", example = "2025-01-01T00:00:00Z")
        private String createdAt;

        @Schema(description = "Subscriber status", example = "ACTIVE")
        private String status;

        @Data
        @Schema(name = "FiltersRequest", description = "Filter object for subscriber")
        public static class FiltersRequest {
            @Schema(description = "Category filter", example = "physics")
            private String category;

            @Schema(description = "Country filter", example = "US")
            private String country;

            @Schema(description = "Prize year filter", example = "2024")
            private Integer prizeYear;
        }
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier of created entity", example = "tch_sub_987")
        private String technicalId;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber entity response")
    public static class SubscriberResponse {
        @Schema(description = "Business identifier for the subscriber", example = "sub_42")
        private String subscriberId;

        @Schema(description = "Display name", example = "Research Team")
        private String name;

        @Schema(description = "Contact endpoint (email or webhook URL)", example = "https://hooks.example.com/notify")
        private String contactEndpoint;

        @Schema(description = "Filter criteria", implementation = SubscriberResponse.FiltersResponse.class)
        private FiltersResponse filters;

        @Schema(description = "Format of notifications", example = "summary")
        private String format;

        @Schema(description = "Subscriber status", example = "ACTIVE")
        private String status;

        @Schema(description = "Creation timestamp (ISO-8601)", example = "2025-01-01T00:00:00Z")
        private String createdAt;

        @Data
        @Schema(name = "FiltersResponse", description = "Filters for the subscriber")
        public static class FiltersResponse {
            @Schema(description = "Category filter", example = "physics")
            private String category;

            @Schema(description = "Country filter", example = "US")
            private String country;

            @Schema(description = "Prize year filter", example = "2024")
            private Integer prizeYear;
        }
    }
}