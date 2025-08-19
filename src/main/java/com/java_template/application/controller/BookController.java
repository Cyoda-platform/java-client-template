package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/books")
@Tag(name = "Book Controller", description = "Read-only endpoints for Books (ingestion/indexing workflows handle creation)")
public class BookController {
    private static final Logger logger = LoggerFactory.getLogger(BookController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BookController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Book", description = "Retrieve a Book by technicalId (openLibraryId)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BookResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(value = "/{technicalId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getBook(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Book.ENTITY_NAME,
                String.valueOf(Book.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            Book book = objectMapper.treeToValue(node, Book.class);

            BookResponse resp = new BookResponse();
            resp.setOpenLibraryId(book.getOpenLibraryId());
            resp.setTitle(book.getTitle());
            resp.setAuthors(book.getAuthors());
            resp.setCoverImageUrl(book.getCoverImageUrl());
            resp.setPublicationYear(book.getPublicationYear());
            resp.setGenres(book.getGenres());
            resp.setSummary(book.getSummary());
            resp.setLastIngestedAt(book.getLastIngestedAt());
            // state field not present in entity class; if present in stored node it would be ignored here

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Validation error getting book: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching book", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching book", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error fetching book", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    static class BookResponse {
        @Schema(description = "Open Library id / technical id")
        private String openLibraryId;
        @Schema(description = "Title")
        private String title;
        @Schema(description = "Authors")
        private java.util.List<String> authors;
        @Schema(description = "Cover image URL")
        private String coverImageUrl;
        @Schema(description = "Publication year")
        private Integer publicationYear;
        @Schema(description = "Genres")
        private java.util.List<String> genres;
        @Schema(description = "Summary")
        private String summary;
        @Schema(description = "Last ingested timestamp")
        private String lastIngestedAt;
    }
}
