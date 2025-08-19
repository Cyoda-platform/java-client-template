package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.searchactivity.version_1.SearchActivity;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/search-activities")
@Tag(name = "SearchActivity Controller", description = "Event-driven endpoints for SearchActivity events")
public class SearchActivityController {
    private static final Logger logger = LoggerFactory.getLogger(SearchActivityController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SearchActivityController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create SearchActivity", description = "Create a search activity event; returns technicalId immediately")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSearchActivity(@RequestBody SearchActivityCreateRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if ((request.getQueryText() == null || request.getQueryText().isBlank()) && (request.getUserId() == null || request.getUserId().isBlank())) {
                // we require queryText for user-facing searches; anonymous analytics could be allowed but keep validation strict per requirements
                throw new IllegalArgumentException("queryText is required for search activities");
            }

            SearchActivity activity = new SearchActivity();
            String activityId = UUID.randomUUID().toString();
            activity.setActivityId(activityId);
            activity.setUserId(request.getUserId());
            activity.setQueryText(request.getQueryText());
            activity.setTimestamp(Instant.now().toString());
            activity.setFilters(request.getFilters() == null ? null : request.getFilters().toString());
            activity.setResultBookIds(null);
            activity.setClickedBookIds(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                SearchActivity.ENTITY_NAME,
                String.valueOf(SearchActivity.ENTITY_VERSION),
                activity
            );

            UUID stored = idFuture.get();
            logger.info("Created SearchActivity stored with id: {} activityId: {}", stored, activityId);

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(activityId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Validation error creating search activity: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating search activity", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating search activity", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error creating search activity", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get SearchActivity", description = "Retrieve a SearchActivity by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SearchActivityResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(value = "/{technicalId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSearchActivity(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                SearchActivity.ENTITY_NAME,
                String.valueOf(SearchActivity.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            SearchActivity activity = objectMapper.treeToValue(node, SearchActivity.class);

            SearchActivityResponse resp = new SearchActivityResponse();
            resp.setActivityId(activity.getActivityId());
            resp.setUserId(activity.getUserId());
            resp.setQueryText(activity.getQueryText());
            resp.setTimestamp(activity.getTimestamp());
            resp.setFilters(activity.getFilters());
            resp.setResultBookIds(activity.getResultBookIds());
            resp.setClickedBookIds(activity.getClickedBookIds());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Validation error getting search activity: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching search activity", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching search activity", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error fetching search activity", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Append Click", description = "Append a clicked bookId to a SearchActivity's clickedBookIds list")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(value = "/{technicalId}/clicks", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> appendClick(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
                                         @RequestBody AppendClickRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null || request.getBookId() == null || request.getBookId().isBlank()) throw new IllegalArgumentException("bookId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                SearchActivity.ENTITY_NAME,
                String.valueOf(SearchActivity.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            SearchActivity activity = objectMapper.treeToValue(node, SearchActivity.class);

            List<String> clicks = activity.getClickedBookIds();
            if (clicks == null) clicks = new ArrayList<>();
            clicks.add(request.getBookId());
            activity.setClickedBookIds(clicks);

            CompletableFuture<UUID> updated = entityService.updateItem(
                SearchActivity.ENTITY_NAME,
                String.valueOf(SearchActivity.ENTITY_VERSION),
                UUID.fromString(technicalId),
                activity
            );

            UUID updatedId = updated.get();
            logger.info("Appended click to SearchActivity {} stored-id {}", technicalId, updatedId);

            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            logger.warn("Validation error appending click: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while appending click", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while appending click", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error appending click", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    static class SearchActivityCreateRequest {
        @Schema(description = "User id (nullable for anonymous)")
        private String userId;
        @Schema(description = "Raw query text")
        private String queryText;
        @Schema(description = "Optional filters (free-form JSON)")
        private ObjectNode filters;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical id of created entity")
        private String technicalId;
    }

    @Data
    static class SearchActivityResponse {
        @Schema(description = "Activity id")
        private String activityId;
        @Schema(description = "User id (nullable)")
        private String userId;
        @Schema(description = "Query text")
        private String queryText;
        @Schema(description = "Timestamp")
        private String timestamp;
        @Schema(description = "Filters (JSON string)")
        private String filters;
        @Schema(description = "Result book ids")
        private List<String> resultBookIds;
        @Schema(description = "Clicked book ids")
        private List<String> clickedBookIds;
    }

    @Data
    static class AppendClickRequest {
        @Schema(description = "Book id that was clicked")
        private String bookId;
    }
}
