package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.validationrecord.version_1.ValidationRecord;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/validation-records")
@Tag(name = "ValidationRecord Controller", description = "Proxy controller for ValidationRecord entity")
public class ValidationRecordController {

    private static final Logger logger = LoggerFactory.getLogger(ValidationRecordController.class);

    private final EntityService entityService;

    public ValidationRecordController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create ValidationRecord", description = "Create a ValidationRecord for an HNItem validation run")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ValidationRecordCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<ValidationRecordCreateResponse> createValidationRecord(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ValidationRecord create request") @RequestBody ValidationRecordCreateRequest request) {
        try {
            if (request == null || request.getTechnicalId() == null || request.getTechnicalId().isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            ValidationRecord record = new ValidationRecord();
            record.setTechnicalId(request.getTechnicalId());
            record.setIsValid(request.getIsValid());
            record.setMissingFields(request.getMissingFields());
            record.setCheckedAt(request.getCheckedAt());
            record.setMessage(request.getMessage());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ValidationRecord.ENTITY_NAME,
                    String.valueOf(ValidationRecord.ENTITY_VERSION),
                    record
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new ValidationRecordCreateResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create ValidationRecord", iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            logger.error("ExecutionException while creating ValidationRecord", ee);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating ValidationRecord", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get ValidationRecord by technicalId", description = "Retrieve a ValidationRecord by its technical id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getValidationRecord(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ValidationRecord.ENTITY_NAME,
                    String.valueOf(ValidationRecord.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get ValidationRecord", iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            logger.error("ExecutionException while retrieving ValidationRecord", ee);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving ValidationRecord", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    public static class ValidationRecordCreateRequest {
        @Schema(description = "Technical id of the validation record (system generated)")
        private String technicalId;

        @Schema(description = "Hacker News item id if present")
        private Long hnItemId;

        @Schema(description = "Validation result flag")
        private Boolean isValid;

        @Schema(description = "Missing fields list if any")
        private java.util.List<String> missingFields;

        @Schema(description = "ISO8601 checked at timestamp")
        private String checkedAt;

        @Schema(description = "Human readable message")
        private String message;
    }

    @Data
    public static class ValidationRecordCreateResponse {
        @Schema(description = "Technical id of the created ValidationRecord")
        private String technicalId;

        public ValidationRecordCreateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
