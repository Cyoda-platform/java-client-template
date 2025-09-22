package com.java_template.application.entity.book.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Book Entity - Represents a book from Open Library API
 * 
 * This entity stores book information including:
 * - Basic book details (title, authors, ISBN)
 * - Publication information (year, publisher)
 * - Classification data (genres, subjects)
 * - Cover image URLs
 * - Search and recommendation metadata
 */
@Data
public class Book implements CyodaEntity {
    public static final String ENTITY_NAME = Book.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier - using Open Library work key
    private String bookId;
    
    // Core book information
    private String title;
    private List<String> authors;
    private String isbn;
    private String openLibraryKey; // Work key from Open Library
    
    // Publication details
    private Integer publicationYear;
    private String publisher;
    private String language;
    private Integer pageCount;
    
    // Classification and categorization
    private List<String> subjects;
    private List<String> genres;
    private String deweyDecimal;
    
    // Cover and media
    private BookCover cover;
    
    // Search and recommendation metadata
    private BookMetadata metadata;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSearchedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return bookId != null && !bookId.trim().isEmpty() &&
               title != null && !title.trim().isEmpty() &&
               authors != null && !authors.isEmpty();
    }

    /**
     * Nested class for book cover information
     */
    @Data
    public static class BookCover {
        private String smallUrl;
        private String mediumUrl;
        private String largeUrl;
        private String thumbnailUrl;
        private Boolean hasImage;
    }

    /**
     * Nested class for search and recommendation metadata
     */
    @Data
    public static class BookMetadata {
        private Integer searchCount;
        private Double popularityScore;
        private Double averageRating;
        private Integer ratingCount;
        private List<String> relatedBooks;
        private LocalDateTime lastUpdated;
        private String dataSource; // "open_library", "manual", etc.
    }
}
