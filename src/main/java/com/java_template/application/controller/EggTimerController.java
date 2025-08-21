package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.eggtimer.version_1.EggTimer;

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
@RequestMapping("/timers")
@Tag(name = "EggTimer API", description = "APIs for managing egg timers")
public class EggTimerController {

    private static final Logger logger = LoggerFactory.getLogger(EggTimerController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EggTimerController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create EggTimer", description = "Creates a new egg timer and returns the technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "409", description = "Conflict")
    })
    @PostMapping
    public ResponseEntity<?> createTimer(@RequestBody TimerCreateRequest request) {
        try {
            if (request == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid request");
            EggTimer timer = new EggTimer();
            timer.setId(request.getId());
            timer.setOwnerUserId(request.getOwnerUserId());
            timer.setBoilType(request.getBoilType());
            timer.setEggSize(request.getEggSize());
            timer.setEggsCount(request.getEggsCount());
            timer.setStartAt(request.getStartAt());
            timer.setDurationSeconds(request.getDurationSeconds());
            timer.setState(request.getState());
            timer.setCreatedAt(request.getCreatedAt());

            ObjectNode node = objectMapper.convertValue(timer, ObjectNode.class);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    EggTimer.ENTITY_NAME,
                    String.valueOf(EggTimer.ENTITY_VERSION),
                    node
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get EggTimer", description = "Retrieve an egg timer by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = EggTimer.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getTimer(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            ObjectNode node = entityService.getItem(
                    EggTimer.ENTITY_NAME,
                    String.valueOf(EggTimer.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List EggTimers", description = "List timers with optional ownerUserId and state filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = EggTimer.class)))
    })
    @GetMapping
    public ResponseEntity<?> listTimers(@RequestParam(required = false) String ownerUserId,
                                        @RequestParam(required = false) String state) {
        try {
            if ((ownerUserId != null && !ownerUserId.isBlank()) || (state != null && !state.isBlank())) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        ownerUserId != null && !ownerUserId.isBlank() ? Condition.of("$.ownerUserId", "EQUALS", ownerUserId) : null,
                        state != null && !state.isBlank() ? Condition.of("$.state", "EQUALS", state) : null
                );
                ArrayNode nodes = entityService.getItemsByCondition(
                        EggTimer.ENTITY_NAME,
                        String.valueOf(EggTimer.ENTITY_VERSION),
                        condition,
                        true
                ).get();
                return ResponseEntity.ok(nodes);
            } else {
                ArrayNode nodes = entityService.getItems(
                        EggTimer.ENTITY_NAME,
                        String.valueOf(EggTimer.ENTITY_VERSION)
                ).get();
                return ResponseEntity.ok(nodes);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update EggTimer", description = "Update an egg timer by technicalId. Use this for pause/resume/cancel actions by providing updated entity fields")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = EggTimer.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @PostMapping("/{technicalId}")
    public ResponseEntity<?> updateTimer(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
                                         @RequestBody TimerUpdateRequest request) {
        try {
            if (request == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid request");
            ObjectNode node = objectMapper.convertValue(request, ObjectNode.class);
            UUID techId = UUID.fromString(technicalId);
            CompletableFuture<java.util.UUID> fut = entityService.updateItem(
                    EggTimer.ENTITY_NAME,
                    String.valueOf(EggTimer.ENTITY_VERSION),
                    techId,
                    node
            );
            UUID updated = fut.get();
            ObjectNode updatedNode = entityService.getItem(
                    EggTimer.ENTITY_NAME,
                    String.valueOf(EggTimer.ENTITY_VERSION),
                    UUID.fromString(updated.toString())
            ).get();
            return ResponseEntity.ok(updatedNode);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class TimerCreateRequest {
        @Schema(description = "Business id of the timer")
        private String id;
        @Schema(description = "Owner user business id")
        private String ownerUserId;
        @Schema(description = "Boil type")
        private String boilType;
        @Schema(description = "Egg size")
        private String eggSize;
        @Schema(description = "Number of eggs")
        private Integer eggsCount;
        @Schema(description = "Start at ISO timestamp")
        private String startAt;
        @Schema(description = "Optional override for duration in seconds")
        private Integer durationSeconds;
        @Schema(description = "Optional state")
        private String state;
        @Schema(description = "Created at ISO timestamp")
        private String createdAt;
    }

    @Data
    static class TimerUpdateRequest {
        @Schema(description = "Partial timer payload for update")
        private String id;
        private String ownerUserId;
        private String boilType;
        private String eggSize;
        private Integer eggsCount;
        private Integer durationSeconds;
        private String startAt;
        private String state;
        private String createdAt;
        private String scheduledStartAt;
        private String expectedEndAt;
        private Integer remainingSeconds;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Internal technical id")
        private String technicalId;
    }
}
