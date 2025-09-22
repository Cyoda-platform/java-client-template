package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BookController - REST API for book management and search operations
 * 
 * This controller provides endpoints for:
 * - Creating and updating book entities
 * - Searching books with filters
 * - Retrieving book details
 * - Managing book metadata and popularity
 */
@RestController
@RequestMapping("/ui/books")
@CrossOrigin(origins = "*")
public class BookController {

    private static final Logger logger = LoggerFactory.getLogger(BookController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public BookController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new book entity
     */
    @PostMapping
    public ResponseEntity<UUID> createBook(@RequestBody Book book) {
        logger.info("Creating new book: {}", book.getTitle());

        try {
            // Set creation timestamp
            book.setCreatedAt(LocalDateTime.now());
            book.setUpdatedAt(LocalDateTime.now());

            // Create the book entity
            EntityWithMetadata<Book> result = entityService.create(book);
            UUID technicalId = result.getId();

            logger.info("Book created successfully with ID: {}", technicalId);
            return ResponseEntity.ok(technicalId);

        } catch (Exception e) {
            logger.error("Failed to create book: {}", book.getTitle(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get book by business ID
     */
    @GetMapping("/{bookId}")
    public ResponseEntity<EntityWithMetadata<Book>> getBook(@PathVariable String bookId) {
        logger.info("Retrieving book with ID: {}", bookId);

        try {
            ModelSpec modelSpec = createBookModelSpec();
            EntityWithMetadata<Book> book = entityService.findByBusinessId(modelSpec, bookId, "bookId", Book.class);

            if (book != null) {
                return ResponseEntity.ok(book);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve book with ID: {}", bookId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update book metadata (triggers BookMetadataProcessor)
     */
    @PutMapping("/{bookId}/metadata")
    public ResponseEntity<UUID> updateBookMetadata(@PathVariable String bookId, @RequestBody Book book) {
        logger.info("Updating metadata for book: {}", bookId);

        try {
            ModelSpec modelSpec = createBookModelSpec();
            
            // Set update timestamp
            book.setUpdatedAt(LocalDateTime.now());

            // Update with manual transition to trigger processor
            EntityWithMetadata<Book> result = entityService.updateByBusinessId(
                book, "bookId", "update_book_metadata");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to update book metadata for ID: {}", bookId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update book popularity score (triggers BookPopularityProcessor)
     */
    @PutMapping("/{bookId}/popularity")
    public ResponseEntity<UUID> updateBookPopularity(@PathVariable String bookId, @RequestBody Book book) {
        logger.info("Updating popularity for book: {}", bookId);

        try {
            ModelSpec modelSpec = createBookModelSpec();
            
            // Set update timestamp
            book.setUpdatedAt(LocalDateTime.now());

            // Update with manual transition to trigger processor
            EntityWithMetadata<Book> result = entityService.updateByBusinessId(
                book, "bookId", "update_popularity_score");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to update book popularity for ID: {}", bookId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Search books with filters
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Book>>> searchBooks(@RequestBody BookSearchRequest searchRequest) {
        logger.info("Searching books with filters: {}", searchRequest);

        try {
            ModelSpec modelSpec = createBookModelSpec();
            List<QueryCondition> conditions = buildSearchConditions(searchRequest);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Book>> results = entityService.search(modelSpec, groupCondition, Book.class);

            logger.info("Found {} books matching search criteria", results.size());
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("Failed to search books", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get books by genre
     */
    @GetMapping("/genre/{genre}")
    public ResponseEntity<List<EntityWithMetadata<Book>>> getBooksByGenre(@PathVariable String genre) {
        logger.info("Retrieving books for genre: {}", genre);

        try {
            ModelSpec modelSpec = createBookModelSpec();
            List<QueryCondition> conditions = new ArrayList<>();

            SimpleCondition genreCondition = new SimpleCondition()
                    .withJsonPath("$.genres")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(genre));
            conditions.add(genreCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Book>> results = entityService.search(modelSpec, groupCondition, Book.class);

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("Failed to retrieve books for genre: {}", genre, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get books by author
     */
    @GetMapping("/author/{author}")
    public ResponseEntity<List<EntityWithMetadata<Book>>> getBooksByAuthor(@PathVariable String author) {
        logger.info("Retrieving books for author: {}", author);

        try {
            ModelSpec modelSpec = createBookModelSpec();
            List<QueryCondition> conditions = new ArrayList<>();

            SimpleCondition authorCondition = new SimpleCondition()
                    .withJsonPath("$.authors")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(author));
            conditions.add(authorCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Book>> results = entityService.search(modelSpec, groupCondition, Book.class);

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("Failed to retrieve books for author: {}", author, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get popular books (sorted by popularity score)
     */
    @GetMapping("/popular")
    public ResponseEntity<List<EntityWithMetadata<Book>>> getPopularBooks(
            @RequestParam(defaultValue = "10") int limit) {
        logger.info("Retrieving top {} popular books", limit);

        try {
            ModelSpec modelSpec = createBookModelSpec();
            
            // In a real implementation, you would add sorting by popularity score
            // For now, we'll return all books (limited by the search implementation)
            List<QueryCondition> conditions = new ArrayList<>();
            
            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Book>> results = entityService.search(modelSpec, groupCondition, Book.class);

            // Limit results (in a real implementation, this would be done in the query)
            if (results.size() > limit) {
                results = results.subList(0, limit);
            }

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("Failed to retrieve popular books", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ModelSpec createBookModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("Book");
        modelSpec.setVersion(1);
        return modelSpec;
    }

    private List<QueryCondition> buildSearchConditions(BookSearchRequest searchRequest) {
        List<QueryCondition> conditions = new ArrayList<>();

        // Title search
        if (searchRequest.getTitle() != null && !searchRequest.getTitle().trim().isEmpty()) {
            SimpleCondition titleCondition = new SimpleCondition()
                    .withJsonPath("$.title")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(searchRequest.getTitle()));
            conditions.add(titleCondition);
        }

        // Author search
        if (searchRequest.getAuthor() != null && !searchRequest.getAuthor().trim().isEmpty()) {
            SimpleCondition authorCondition = new SimpleCondition()
                    .withJsonPath("$.authors")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(searchRequest.getAuthor()));
            conditions.add(authorCondition);
        }

        // Genre filter
        if (searchRequest.getGenre() != null && !searchRequest.getGenre().trim().isEmpty()) {
            SimpleCondition genreCondition = new SimpleCondition()
                    .withJsonPath("$.genres")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(searchRequest.getGenre()));
            conditions.add(genreCondition);
        }

        // Publication year range
        if (searchRequest.getPublicationYearStart() != null) {
            SimpleCondition yearStartCondition = new SimpleCondition()
                    .withJsonPath("$.publicationYear")
                    .withOperation(Operation.GREATER_OR_EQUAL)
                    .withValue(objectMapper.valueToTree(searchRequest.getPublicationYearStart()));
            conditions.add(yearStartCondition);
        }

        if (searchRequest.getPublicationYearEnd() != null) {
            SimpleCondition yearEndCondition = new SimpleCondition()
                    .withJsonPath("$.publicationYear")
                    .withOperation(Operation.LESS_OR_EQUAL)
                    .withValue(objectMapper.valueToTree(searchRequest.getPublicationYearEnd()));
            conditions.add(yearEndCondition);
        }

        return conditions;
    }

    /**
     * Request DTO for book search
     */
    @Getter
    @Setter
    public static class BookSearchRequest {
        private String title;
        private String author;
        private String genre;
        private Integer publicationYearStart;
        private Integer publicationYearEnd;
        private String language;
    }
}
