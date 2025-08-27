package com.java_template.application.controller.book.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "Book Controller", description = "CRUD proxy endpoints for Book entity (version 1)")
public class BookController {

    private static final Logger logger = LoggerFactory.getLogger(BookController.class);

    private final EntityService entityService;

    public BookController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Book", description = "Persist a single Book entity. Returns technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createBook(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Book create request", required = true,
                    content = @Content(schema = @Schema(implementation = BookRequest.class)))
            @RequestBody BookRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Book book = mapToEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Book.ENTITY_NAME,
                    String.valueOf(Book.ENTITY_VERSION),
                    book
            );
            UUID id = idFuture.get();
            IdResponse resp = new IdResponse(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create book: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error on createBook", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating book", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error on createBook", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Books", description = "Persist multiple Book entities. Returns list of technicalIds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createBooksBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Book create requests", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = BookRequest.class))))
            @RequestBody List<BookRequest> requests) {
        try {
            if (requests == null) throw new IllegalArgumentException("Request body is required");
            List<Book> books = new ArrayList<>();
            for (BookRequest r : requests) {
                books.add(mapToEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Book.ENTITY_NAME,
                    String.valueOf(Book.ENTITY_VERSION),
                    books
            );
            List<UUID> ids = idsFuture.get();
            BulkIdResponse resp = new BulkIdResponse();
            resp.setTechnicalIds(new ArrayList<>());
            for (UUID u : ids) resp.getTechnicalIds().add(u.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid bulk create request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error on createBooksBulk", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating books bulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error on createBooksBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Book by technicalId", description = "Retrieve a Book by its technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BookResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getBookById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Book.ENTITY_NAME,
                    String.valueOf(Book.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to get book: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error on getBookById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting book", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error on getBookById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Books", description = "Retrieve all stored Book entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BookResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllBooks() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Book.ENTITY_NAME,
                    String.valueOf(Book.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error on getAllBooks", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting all books", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error on getAllBooks", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Books by condition", description = "Retrieve Books matching the provided SearchConditionRequest")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BookResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchBooks(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Book.ENTITY_NAME,
                    String.valueOf(Book.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid search request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error on searchBooks", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching books", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error on searchBooks", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Book", description = "Update an existing Book entity by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateBook(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Book update request", required = true,
                    content = @Content(schema = @Schema(implementation = BookRequest.class)))
            @RequestBody BookRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID uuid = UUID.fromString(technicalId);
            Book book = mapToEntity(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Book.ENTITY_NAME,
                    String.valueOf(Book.ENTITY_VERSION),
                    uuid,
                    book
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid update request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error on updateBook", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating book", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error on updateBook", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Book", description = "Delete an existing Book entity by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteBook(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Book.ENTITY_NAME,
                    String.valueOf(Book.ENTITY_VERSION),
                    uuid
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid delete request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error on deleteBook", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting book", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error on deleteBook", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Helper mapper - keep mapping simple and free of business logic
    private Book mapToEntity(@Nullable BookRequest request) {
        if (request == null) return null;
        Book book = new Book();
        book.setId(request.getId());
        book.setTitle(request.getTitle());
        book.setDescription(request.getDescription());
        book.setExcerpt(request.getExcerpt());
        book.setFetchTimestamp(request.getFetchTimestamp());
        book.setIsPopular(request.getIsPopular());
        book.setPageCount(request.getPageCount());
        book.setPopularityScore(request.getPopularityScore());
        book.setPublishDate(request.getPublishDate());
        return book;
    }

    // DTOs

    @Data
    public static class IdResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;

        public IdResponse() {}

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    public static class BulkIdResponse {
        @Schema(description = "List of technical IDs")
        private List<String> technicalIds;
    }

    @Data
    public static class BookRequest {
        @Schema(description = "Book id (technical)", example = "17")
        private Integer id;

        @Schema(description = "Book title", example = "The Art of Testing")
        private String title;

        @Schema(description = "Full description")
        private String description;

        @Schema(description = "Short excerpt")
        private String excerpt;

        @Schema(description = "ISO timestamp when record was fetched", example = "2025-08-27T09:00:10Z")
        private String fetchTimestamp;

        @Schema(description = "Is the book popular", example = "true")
        private Boolean isPopular;

        @Schema(description = "Number of pages", example = "420")
        private Integer pageCount;

        @Schema(description = "Popularity score", example = "92.3")
        private Double popularityScore;

        @Schema(description = "ISO publish date", example = "2023-05-12")
        private String publishDate;
    }

    @Data
    public static class BookResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;

        @Schema(description = "Book id (technical)")
        private Integer id;

        @Schema(description = "Book title")
        private String title;

        @Schema(description = "Full description")
        private String description;

        @Schema(description = "Number of pages")
        private Integer pageCount;

        @Schema(description = "Short excerpt")
        private String excerpt;

        @Schema(description = "ISO publish date")
        private String publishDate;

        @Schema(description = "ISO fetch timestamp")
        private String fetchTimestamp;

        @Schema(description = "Popularity score")
        private Double popularityScore;

        @Schema(description = "Is popular flag")
        private Boolean isPopular;
    }
}