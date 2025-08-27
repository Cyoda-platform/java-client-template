package com.java_template.application.controller.getuserjob.version_1;

import static com.java_template.common.config.Config.*;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.getuserjob.version_1.GetUserJob;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/jobs")
@Tag(name = "GetUserJob Controller", description = "Controller proxy for GetUserJob entity (version 1)")
public class GetUserJobController {

    private static final Logger logger = LoggerFactory.getLogger(GetUserJobController.class);

    private final EntityService entityService;

    public GetUserJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create GetUserJob", description = "Creates a GetUserJob orchestration and returns a technicalId. Business processing is handled asynchronously by workflows.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateGetUserJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/get-user", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createGetUserJob(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request to create GetUserJob", required = true,
                    content = @Content(schema = @Schema(implementation = CreateGetUserJobRequest.class)))
            @RequestBody CreateGetUserJobRequest request) {
        try {
            if (request == null || request.getUserId() == null || request.getUserId().isBlank()) {
                throw new IllegalArgumentException("userId is required");
            }

            // Build minimal GetUserJob entity; workflows handle further business processing.
            GetUserJob job = new GetUserJob();
            job.setRequestUserId(request.getUserId());
            job.setCreatedAt(Instant.now().toString());
            job.setStatus("CREATED");

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    GetUserJob.ENTITY_NAME,
                    String.valueOf(GetUserJob.ENTITY_VERSION),
                    job
            );

            UUID technicalId = idFuture.get();
            CreateGetUserJobResponse response = new CreateGetUserJobResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create GetUserJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating GetUserJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating GetUserJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while creating GetUserJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get GetUserJob by technicalId", description = "Retrieves the GetUserJob orchestration state and related result by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetUserJobResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getGetUserJobById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    GetUserJob.ENTITY_NAME,
                    String.valueOf(GetUserJob.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            // Return raw ObjectNode produced by EntityService. DTO class provided for documentation.
            return ResponseEntity.ok(node);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get GetUserJob: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving GetUserJob", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving GetUserJob", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving GetUserJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Request/Response DTOs

    @Data
    @Schema(name = "CreateGetUserJobRequest", description = "Request payload to create a GetUserJob")
    public static class CreateGetUserJobRequest {
        @Schema(name = "userId", description = "User id provided by caller", required = true, example = "2")
        private String userId;
    }

    @Data
    @Schema(name = "CreateGetUserJobResponse", description = "Response after creating a GetUserJob containing technicalId")
    public static class CreateGetUserJobResponse {
        @Schema(name = "technicalId", description = "Technical id of the created job", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "GetUserJobResponse", description = "Representation of GetUserJob with possible result and user")
    public static class GetUserJobResponse {
        @Schema(name = "technicalId", description = "Technical id of the job")
        private String technicalId;

        @Schema(name = "request_user_id", description = "User id provided by caller")
        private String request_user_id;

        @Schema(name = "status", description = "Job status")
        private String status;

        @Schema(name = "created_at", description = "ISO timestamp when job was created")
        private String created_at;

        @Schema(name = "started_at", description = "ISO timestamp when processing started")
        private String started_at;

        @Schema(name = "completed_at", description = "ISO timestamp when processing completed")
        private String completed_at;

        @Schema(name = "response_code", description = "HTTP response code from external service")
        private Integer response_code;

        @Schema(name = "error_message", description = "Error details if any")
        private String error_message;

        @Schema(name = "result", description = "Result object when available")
        private GetUserResultDto result;
    }

    @Data
    @Schema(name = "GetUserResult", description = "GetUserResult payload")
    public static class GetUserResultDto {
        @Schema(name = "job_reference", description = "Reference to GetUserJob")
        private String job_reference;

        @Schema(name = "status", description = "Result status (SUCCESS NOT_FOUND INVALID_INPUT ERROR)")
        private String status;

        @Schema(name = "retrieved_at", description = "ISO timestamp when result was produced")
        private String retrieved_at;

        @Schema(name = "error_message", description = "Error message if present")
        private String error_message;

        @Schema(name = "user", description = "User payload when status == SUCCESS")
        private UserDto user;
    }

    @Data
    @Schema(name = "User", description = "User entity snapshot")
    public static class UserDto {
        @Schema(name = "id", description = "Business identifier returned by external service")
        private Integer id;

        @Schema(name = "email", description = "User email")
        private String email;

        @Schema(name = "first_name", description = "First name")
        private String first_name;

        @Schema(name = "last_name", description = "Last name")
        private String last_name;

        @Schema(name = "avatar", description = "Avatar URL")
        private String avatar;

        @Schema(name = "retrieved_at", description = "ISO timestamp when user was retrieved")
        private String retrieved_at;

        @Schema(name = "source", description = "Source system of the user data")
        private String source;
    }
}