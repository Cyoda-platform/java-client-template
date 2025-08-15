package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/fetch-jobs")
@Tag(name = "FetchJob API", description = "Operations for FetchJob orchestration entity")
public class FetchJobController {
    private static final Logger logger = LoggerFactory.getLogger(FetchJobController.class);
    private final EntityService entityService;

    public FetchJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create FetchJob", description = "Create a FetchJob and trigger the fetch orchestration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request")
    })
    @RequestMapping(method = RequestMethod.POST, path = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createFetchJob(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "FetchJob creation payload") @RequestBody FetchJobRequest request) {
        try {
            if (request == null || request.getRequestDate() == null || request.getRequestDate().isBlank()) {
                throw new IllegalArgumentException("request_date is required and must be YYYY-MM-DD");
            }

            FetchJob job = new FetchJob();
            job.setRequestDate(request.getRequestDate());
            job.setScheduledTime(request.getScheduledTime());
            // initial status set by workflow, but allow client to set if provided
            job.setStatus(request.getStatus());

            UUID id = entityService.addItem(
                FetchJob.ENTITY_NAME,
                String.valueOf(FetchJob.ENTITY_VERSION),
                job
            ).get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid FetchJob request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error creating FetchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating FetchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get FetchJob by id", description = "Retrieve FetchJob by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
        @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @RequestMapping(method = RequestMethod.GET, path = "{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getFetchJob(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            ObjectNode node = entityService.getItem(
                FetchJob.ENTITY_NAME,
                String.valueOf(FetchJob.ENTITY_VERSION),
                UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(node);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error getting FetchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error getting FetchJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class FetchJobRequest {
        @Schema(description = "Request date in YYYY-MM-DD")
        private String requestDate;
        @Schema(description = "Scheduled time e.g. 18:00Z")
        private String scheduledTime;
        @Schema(description = "Force creation (optional)")
        private Boolean force = false;
        @Schema(description = "Optional status override")
        private String status;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical id of created entity")
        private String technicalId;
    }
}
