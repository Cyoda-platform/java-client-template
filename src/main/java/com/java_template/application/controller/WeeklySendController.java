package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.weeklysend.version_1.WeeklySend;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/weekly-sends")
@Tag(name = "WeeklySend")
public class WeeklySendController {
    private static final Logger logger = LoggerFactory.getLogger(WeeklySendController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public WeeklySendController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create WeeklySend", description = "Create a WeeklySend orchestration and start workflow as appropriate. Returns technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createWeeklySend(@RequestBody WeeklySendCreateRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            WeeklySend data = new WeeklySend();
            data.setId(UUID.randomUUID().toString());
            data.setCatfact_id(request.getCatfact_id());
            data.setScheduled_date(request.getScheduled_date());
            // decide status based on scheduled_date
            if (request.getScheduled_date() != null && !request.getScheduled_date().isBlank()) {
                data.setStatus("scheduled");
            } else {
                data.setStatus("draft");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    WeeklySend.ENTITY_NAME,
                    String.valueOf(WeeklySend.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            logger.error("Execution error creating weekly send", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating weekly send", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error creating weekly send", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get WeeklySend", description = "Retrieve WeeklySend by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WeeklySendResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<WeeklySendResponse> getWeeklySend(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    WeeklySend.ENTITY_NAME,
                    String.valueOf(WeeklySend.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            WeeklySendResponse resp = mapper.treeToValue(node, WeeklySendResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            logger.error("Execution error getting weekly send", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting weekly send", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error getting weekly send", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    static class WeeklySendCreateRequest {
        @Schema(description = "CatFact domain id", example = "uuid-string")
        private String catfact_id;
        @Schema(description = "Scheduled date (ISO)", example = "2025-08-15T09:00:00Z")
        private String scheduled_date;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) { this.technicalId = technicalId; }
    }

    @Data
    static class WeeklySendResponse {
        @Schema(description = "Technical ID of the weekly send")
        private String technicalId;
        @Schema(description = "Domain id of the weekly send")
        private String id;
        @Schema(description = "Linked CatFact id")
        private String catfact_id;
        @Schema(description = "Scheduled date (ISO)")
        private String scheduled_date;
        @Schema(description = "Actual send date (ISO)")
        private String actual_send_date;
        @Schema(description = "Recipients count")
        private Integer recipients_count;
        @Schema(description = "Opens count")
        private Integer opens_count;
        @Schema(description = "Clicks count")
        private Integer clicks_count;
        @Schema(description = "Unsubscribes count")
        private Integer unsubscribes_count;
        @Schema(description = "Bounces count")
        private Integer bounces_count;
        @Schema(description = "Status")
        private String status;
        @Schema(description = "Error details if any")
        private String error_details;
    }
}
