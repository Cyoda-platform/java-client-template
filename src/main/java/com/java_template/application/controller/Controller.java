package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.catfactjob.version_1.CatFactJob;
import com.java_template.application.entity.emaildispatch.version_1.EmailDispatch;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
@Tag(name = "Weekly Cat Fact Subscription API", description = "Event-driven REST API for managing CatFactJob, Subscriber, and EmailDispatch entities")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // --- CatFactJob Endpoints ---

    @Operation(summary = "Create new CatFactJob", description = "Create a new CatFactJob entity with scheduledAt timestamp (triggers cat fact fetching & emailing)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CatFactJob created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/catFactJob")
    public ResponseEntity<?> createCatFactJob(@RequestBody CatFactJobRequest request) {
        try {
            CatFactJob job = new CatFactJob();
            job.setScheduledAt(request.getScheduledAt());
            job.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    CatFactJob.ENTITY_NAME,
                    String.valueOf(CatFactJob.ENTITY_VERSION),
                    job
            );
            UUID technicalId = idFuture.get();

            TechnicalIdResponse response = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Entity not found");
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid arguments");
            } else {
                logger.error("ExecutionException in createCatFactJob", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid arguments");
        } catch (Exception e) {
            logger.error("Exception in createCatFactJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Get CatFactJob by technicalId", description = "Retrieve CatFactJob details by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CatFactJob entity JSON", content = @Content(schema = @Schema(implementation = CatFactJob.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "CatFactJob not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/catFactJob/{technicalId}")
    public ResponseEntity<?> getCatFactJobById(@Parameter(description = "Technical ID of the CatFactJob") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    CatFactJob.ENTITY_NAME,
                    String.valueOf(CatFactJob.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CatFactJob not found");
            }
            return ResponseEntity.ok(item);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CatFactJob not found");
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid technicalId");
            } else {
                logger.error("ExecutionException in getCatFactJobById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid technicalId");
        } catch (Exception e) {
            logger.error("Exception in getCatFactJobById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // --- Subscriber Endpoints ---

    @Operation(summary = "Create new Subscriber", description = "Create a new Subscriber (sign-up) with email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriber created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/subscriber")
    public ResponseEntity<?> createSubscriber(@RequestBody SubscriberRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(request.getEmail());
            subscriber.setSubscribedAt(java.time.OffsetDateTime.now().toString());
            subscriber.setUnsubscribedAt(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber
            );
            UUID technicalId = idFuture.get();

            TechnicalIdResponse response = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Entity not found");
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid arguments");
            } else {
                logger.error("ExecutionException in createSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid arguments");
        } catch (Exception e) {
            logger.error("Exception in createSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve Subscriber details by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriber entity JSON", content = @Content(schema = @Schema(implementation = Subscriber.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "Subscriber not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/subscriber/{technicalId}")
    public ResponseEntity<?> getSubscriberById(@Parameter(description = "Technical ID of the Subscriber") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            return ResponseEntity.ok(item);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid technicalId");
            } else {
                logger.error("ExecutionException in getSubscriberById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid technicalId");
        } catch (Exception e) {
            logger.error("Exception in getSubscriberById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // --- Unsubscribe Endpoint ---

    @Operation(summary = "Unsubscribe a subscriber by email", description = "Unsubscribe a subscriber by email address")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unsubscribe event created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribeSubscriber(@RequestBody UnsubscribeRequest request) {
        try {
            // We create a Subscriber with unsubscribedAt set to now to mark unsubscribe event
            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(request.getEmail());
            subscriber.setSubscribedAt(null); // unknown here
            subscriber.setUnsubscribedAt(java.time.OffsetDateTime.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    subscriber
            );
            UUID technicalId = idFuture.get();

            TechnicalIdResponse response = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Entity not found");
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid arguments");
            } else {
                logger.error("ExecutionException in unsubscribeSubscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid arguments");
        } catch (Exception e) {
            logger.error("Exception in unsubscribeSubscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // --- EmailDispatch Endpoints ---

    @Operation(summary = "Get EmailDispatch by technicalId", description = "Retrieve EmailDispatch details by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "EmailDispatch entity JSON", content = @Content(schema = @Schema(implementation = EmailDispatch.class))),
            @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
            @ApiResponse(responseCode = "404", description = "EmailDispatch not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/emailDispatch/{technicalId}")
    public ResponseEntity<?> getEmailDispatchById(@Parameter(description = "Technical ID of the EmailDispatch") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    EmailDispatch.ENTITY_NAME,
                    String.valueOf(EmailDispatch.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EmailDispatch not found");
            }
            return ResponseEntity.ok(item);

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EmailDispatch not found");
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid technicalId");
            } else {
                logger.error("ExecutionException in getEmailDispatchById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid technicalId");
        } catch (Exception e) {
            logger.error("Exception in getEmailDispatchById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // --- Reports Endpoints ---

    @Operation(summary = "Get total number of active subscribers", description = "Retrieve total count of active subscribers (unsubscribedAt == null)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Count of active subscribers", content = @Content(schema = @Schema(implementation = CountResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/report/subscribersCount")
    public ResponseEntity<?> getActiveSubscribersCount() {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.unsubscribedAt", "EQUALS", null)
            );
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            int count = items != null ? items.size() : 0;
            CountResponse response = new CountResponse(count);
            return ResponseEntity.ok(response);

        } catch (ExecutionException e) {
            logger.error("ExecutionException in getActiveSubscribersCount", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        } catch (Exception e) {
            logger.error("Exception in getActiveSubscribersCount", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Operation(summary = "Get total number of emails sent weekly", description = "Retrieve total count of emails sent weekly (EmailDispatch entities count)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Count of emails sent", content = @Content(schema = @Schema(implementation = CountResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/report/emailsSentCount")
    public ResponseEntity<?> getEmailsSentCount() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    EmailDispatch.ENTITY_NAME,
                    String.valueOf(EmailDispatch.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            int count = items != null ? items.size() : 0;
            CountResponse response = new CountResponse(count);
            return ResponseEntity.ok(response);

        } catch (ExecutionException e) {
            logger.error("ExecutionException in getEmailsSentCount", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        } catch (Exception e) {
            logger.error("Exception in getEmailsSentCount", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // --- Static DTO classes ---

    @Data
    public static class CatFactJobRequest {
        @Schema(description = "ISO8601 datetime string for when the job is scheduled", example = "2024-07-01T10:00:00Z")
        private String scheduledAt;
    }

    @Data
    public static class SubscriberRequest {
        @Schema(description = "Subscriber's email address", example = "user@example.com")
        private String email;
    }

    @Data
    public static class UnsubscribeRequest {
        @Schema(description = "Subscriber's email address to unsubscribe", example = "user@example.com")
        private String email;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    public static class CountResponse {
        @Schema(description = "Count number")
        private int count;

        public CountResponse(int count) {
            this.count = count;
        }
    }
}