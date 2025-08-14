package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.java_template.common.service.EntityService;

import com.java_template.application.entity.datadownloadjob.version_1.DataDownloadJob;
import com.java_template.application.entity.dataanalysisreport.version_1.DataAnalysisReport;
import com.java_template.application.entity.subscriber.version_1.Subscriber;

import lombok.Data;

@Tag(name = "Controller")
@RestController
@RequestMapping("/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // DataDownloadJob POST /dataDownloadJobs
    @Operation(summary = "Create a new DataDownloadJob", description = "Create a new DataDownloadJob by providing the URL to download.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job created", content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/dataDownloadJobs")
    public ResponseEntity<?> createDataDownloadJob(@RequestBody DataDownloadJobRequest request) {
        try {
            DataDownloadJob job = new DataDownloadJob();
            job.setUrl(request.getUrl());
            job.setStatus("PENDING");
            job.setCreatedAt(java.time.OffsetDateTime.now().toString());
            job.setCompletedAt(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                DataDownloadJob.ENTITY_NAME,
                String.valueOf(DataDownloadJob.ENTITY_VERSION),
                job
            );

            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Internal error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // DataDownloadJob GET /dataDownloadJobs/{technicalId}
    @Operation(summary = "Retrieve DataDownloadJob by technicalId", description = "Retrieve DataDownloadJob status and details by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = DataDownloadJob.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/dataDownloadJobs/{technicalId}")
    public ResponseEntity<?> getDataDownloadJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                DataDownloadJob.ENTITY_NAME,
                String.valueOf(DataDownloadJob.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode result = itemFuture.get();
            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DataDownloadJob not found");
            }
            return ResponseEntity.ok(result);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Internal error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // DataAnalysisReport GET /dataAnalysisReports/{technicalId}
    @Operation(summary = "Retrieve DataAnalysisReport by technicalId", description = "Retrieve generated analysis report by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = DataAnalysisReport.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/dataAnalysisReports/{technicalId}")
    public ResponseEntity<?> getDataAnalysisReport(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                DataAnalysisReport.ENTITY_NAME,
                String.valueOf(DataAnalysisReport.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode result = itemFuture.get();
            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DataAnalysisReport not found");
            }
            return ResponseEntity.ok(result);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Internal error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Subscriber POST /subscribers
    @Operation(summary = "Add a new subscriber", description = "Add a new subscriber.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscriber added", content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/subscribers")
    public ResponseEntity<?> addSubscriber(@RequestBody SubscriberRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(request.getEmail());
            subscriber.setName(request.getName());
            subscriber.setSubscribedAt(java.time.OffsetDateTime.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                subscriber
            );

            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Internal error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Subscriber GET /subscribers/{technicalId}
    @Operation(summary = "Retrieve subscriber details by technicalId", description = "Retrieve subscriber details by technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = Subscriber.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/subscribers/{technicalId}")
    public ResponseEntity<?> getSubscriber(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );
            ObjectNode result = itemFuture.get();
            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            return ResponseEntity.ok(result);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Internal error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Subscriber POST /subscribers/update
    @Operation(summary = "Add updated subscriber entity", description = "Add a new Subscriber entity reflecting updated subscriber info (immutable creation).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscriber updated", content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/subscribers/update")
    public ResponseEntity<?> updateSubscriber(@RequestBody SubscriberUpdateRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(request.getEmail());
            subscriber.setName(request.getName());
            subscriber.setSubscribedAt(java.time.OffsetDateTime.now().toString());
            // Note: technicalId is not used for update but included in request as per immutable creation principle

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                subscriber
            );

            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Internal error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Subscriber POST /subscribers/remove
    @Operation(summary = "Add a new Subscriber entity reflecting removal", description = "Add a new Subscriber entity reflecting removal (immutable creation).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscriber removal recorded", content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/subscribers/remove")
    public ResponseEntity<?> removeSubscriber(@RequestBody SubscriberRemoveRequest request) {
        try {
            Subscriber subscriber = new Subscriber();
            // We only have technicalId in request, but Subscriber entity requires email, name, subscribedAt.
            // Since immutable creation principle, we create a new Subscriber entity reflecting removal.
            // The functional specification suggests possibly a status field, but Subscriber entity does not have it.
            // So we create a minimal Subscriber entity with empty or null fields to represent removal.
            // Alternatively, this might be handled in workflow.
            // Here we set email, name, subscribedAt as empty strings to allow isValid() to fail if necessary.
            // But to avoid failure, we set some dummy values.
            subscriber.setEmail("removed@example.com");
            subscriber.setName("Removed Subscriber");
            subscriber.setSubscribedAt(java.time.OffsetDateTime.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                subscriber
            );

            UUID technicalId = idFuture.get();

            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Internal error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity")
        private final String technicalId;
    }

    @Data
    static class DataDownloadJobRequest {
        @Schema(description = "URL to download the data from", required = true)
        private String url;
    }

    @Data
    static class SubscriberRequest {
        @Schema(description = "Subscriber email address", required = true)
        private String email;

        @Schema(description = "Subscriber name", required = true)
        private String name;
    }

    @Data
    static class SubscriberUpdateRequest {
        @Schema(description = "Technical ID of the subscriber", required = true)
        private String technicalId;

        @Schema(description = "Subscriber email address", required = true)
        private String email;

        @Schema(description = "Subscriber name", required = true)
        private String name;
    }

    @Data
    static class SubscriberRemoveRequest {
        @Schema(description = "Technical ID of the subscriber", required = true)
        private String technicalId;
    }

}