package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.book.version_1.Book;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/books")
@Tag(name = "Book Controller", description = "Endpoints to retrieve Book entities (system-managed)")
public class BookController {
    private static final Logger logger = LoggerFactory.getLogger(BookController.class);

    private final EntityService entityService;

    public BookController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Book by source id", description = "Retrieve a Book by its upstream source id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BookResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getBookById(
            @Parameter(name = "id", description = "Upstream source id of the book") @PathVariable("id") Integer id
    ) {
        try {
            if (id == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("id is required");
            }

            SearchConditionRequest condition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.id", "EQUALS", String.valueOf(id))
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Book.ENTITY_NAME,
                    String.valueOf(Book.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arr = itemsFuture.get();
            if (arr == null || arr.size() == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Book not found");
            }

            JsonNode node = arr.get(0);
            return ResponseEntity.ok(new BookResponse(node));

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution failed", cause != null ? cause : ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Execution error");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class BookResponse {
        @Schema(description = "Book JSON payload")
        private final JsonNode book;
    }
}
