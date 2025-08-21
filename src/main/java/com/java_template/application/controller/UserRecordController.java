package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.userrecord.version_1.UserRecord;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/users")
@Tag(name = "UserRecord API", description = "Proxy controller for UserRecord entity")
public class UserRecordController {
    private static final Logger logger = LoggerFactory.getLogger(UserRecordController.class);

    private final EntityService entityService;

    public UserRecordController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get UserRecord by technicalId", description = "Retrieve a UserRecord by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserRecordResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUserRecord(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    UserRecord.ENTITY_NAME,
                    String.valueOf(UserRecord.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getUserRecord", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error getting UserRecord", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error getting UserRecord", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Query UserRecords", description = "Query UserRecords by email or externalId. If no parameter provided returns all records.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = UserRecordResponse.class))))
    })
    @GetMapping
    public ResponseEntity<?> queryUserRecords(@RequestParam(required = false) String email,
                                              @RequestParam(required = false) Integer externalId) {
        try {
            if (email != null && !email.isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.email", "EQUALS", email)
                );
                CompletableFuture<ArrayNode> filtered = entityService.getItemsByCondition(
                        UserRecord.ENTITY_NAME,
                        String.valueOf(UserRecord.ENTITY_VERSION),
                        condition,
                        true
                );
                ArrayNode arr = filtered.get();
                return ResponseEntity.ok(arr);
            } else if (externalId != null) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.externalId", "EQUALS", String.valueOf(externalId))
                );
                CompletableFuture<ArrayNode> filtered = entityService.getItemsByCondition(
                        UserRecord.ENTITY_NAME,
                        String.valueOf(UserRecord.ENTITY_VERSION),
                        condition,
                        true
                );
                ArrayNode arr = filtered.get();
                return ResponseEntity.ok(arr);
            } else {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        UserRecord.ENTITY_NAME,
                        String.valueOf(UserRecord.ENTITY_VERSION)
                );
                ArrayNode arr = itemsFuture.get();
                return ResponseEntity.ok(arr);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in queryUserRecords", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution error querying UserRecords", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error querying UserRecords", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "UserRecordResponse", description = "UserRecord response payload")
    public static class UserRecordResponse {
        private String technicalId;
        private Integer externalId;
        private String firstName;
        private String lastName;
        private String email;
        private String transformedAt;
        private String storedAt;
        private Boolean normalized;
        private String status;
        private String errorMessage;
    }
}
