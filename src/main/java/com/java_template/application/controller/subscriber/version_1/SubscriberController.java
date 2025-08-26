package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Nullable;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/api/v1/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Subscriber", description = "Subscriber entity proxy API (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EntityService entityService;

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Register Subscriber", description = "Register a new Subscriber. Persists the entity and returns the technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber payload", required = true,
                    content = @Content(schema = @Schema(implementation = SubscriberRequest.class)))
            @RequestBody SubscriberRequest requestBody
    ) {
        try {
            if (requestBody == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Subscriber entity = new Subscriber();
            entity.setId(requestBody.getId());
            entity.setName(requestBody.getName());
            entity.setActive(requestBody.getActive());
            entity.setCreatedAt(requestBody.getCreatedAt());

            if (requestBody.getContact() != null) {
                Subscriber.Contact contact = new Subscriber.Contact();
                contact.setEmail(requestBody.getContact().getEmail());
                entity.setContact(contact);
            }

            entity.setSubscribedCategories(requestBody.getSubscribedCategories());

            if (requestBody.getSubscribedYearRange() != null) {
                Subscriber.YearRange yr = new Subscriber.YearRange();
                yr.setFrom(requestBody.getSubscribedYearRange().getFrom());
                yr.setTo(requestBody.getSubscribedYearRange().getTo());
                entity.setSubscribedYearRange(yr);
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    entity
            );

            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create subscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating subscriber", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when creating subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber", description = "Retrieve a Subscriber by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalIdStr
    ) {
        try {
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

            // Ensure technicalId is present in response payload
            node.put("technicalId", technicalId.toString());

            SubscriberResponse resp = MAPPER.treeToValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId format: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving subscriber", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving subscriber", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "List Subscribers", description = "Retrieve all Subscribers.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, params = "!technicalId")
    public ResponseEntity<?> listSubscribers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when listing subscribers", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when listing subscribers", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "SubscriberRequest", description = "Subscriber request payload")
    public static class SubscriberRequest {
        @Schema(description = "Business id of the subscriber", example = "sub-42")
        private String id;

        @Schema(description = "Subscriber name", example = "Alice")
        private String name;

        @Schema(description = "Contact information")
        private ContactDto contact;

        @Schema(description = "Subscribed categories")
        private List<String> subscribedCategories;

        @Schema(description = "Subscribed year range")
        private YearRangeDto subscribedYearRange;

        @Schema(description = "Active flag", example = "true")
        private Boolean active;

        @Schema(description = "Created timestamp (ISO-8601)", example = "2025-08-26T11:00:00Z")
        private String createdAt;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response containing the technical id")
    public static class CreateResponse {
        @Schema(description = "Technical id generated by the datastore")
        private String technicalId;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber response payload")
    public static class SubscriberResponse {
        @Schema(description = "Technical id of the entity")
        private String technicalId;

        @Schema(description = "Business id of the subscriber")
        private String id;

        @Schema(description = "Subscriber name")
        private String name;

        @Schema(description = "Contact information")
        private ContactDto contact;

        @Schema(description = "Subscribed categories")
        private List<String> subscribedCategories;

        @Schema(description = "Subscribed year range")
        private YearRangeDto subscribedYearRange;

        @Schema(description = "Active flag")
        private Boolean active;

        @Schema(description = "Created timestamp (ISO-8601)")
        private String createdAt;
    }

    @Data
    @Schema(name = "ContactDto", description = "Contact DTO")
    public static class ContactDto {
        @Schema(description = "Email address", example = "alice@example.com")
        private String email;
    }

    @Data
    @Schema(name = "YearRangeDto", description = "Year range DTO")
    public static class YearRangeDto {
        @Schema(description = "From year", example = "1900")
        private String from;

        @Schema(description = "To year", example = "1950")
        private String to;
    }
}