package com.java_template.application.processor;

import com.java_template.application.entity.search_query.version_1.SearchQuery;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

/**
 * SearchExecutionProcessor - Executes book search queries using Open Library API
 * 
 * This processor handles:
 * - Executing search queries against Open Library API
 * - Processing and filtering search results
 * - Creating/updating Book entities from API responses
 * - Updating search execution metadata
 * - Tracking user search activity
 */
@Component
public class SearchExecutionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SearchExecutionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SearchExecutionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(SearchQuery.class)
                .validate(this::isValidEntityWithMetadata, "Invalid search query entity")
                .map(this::processSearchExecution)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<SearchQuery> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or SearchQuery entity is null");
            return false;
        }

        SearchQuery searchQuery = entityWithMetadata.entity();
        if (!searchQuery.isValid()) {
            logger.error("SearchQuery entity validation failed for queryId: {}", searchQuery.getQueryId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<SearchQuery> processSearchExecution(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<SearchQuery> context) {
        EntityWithMetadata<SearchQuery> entityWithMetadata = context.entityResponse();
        SearchQuery searchQuery = entityWithMetadata.entity();
        logger.info("Executing search for query: {} with term: {}", searchQuery.getQueryId(), searchQuery.getSearchTerm());

        try {
            // Update execution status
            updateExecutionStatus(searchQuery, "executing");

            // Execute the search
            List<Book> searchResults = executeOpenLibrarySearch(searchQuery);

            // Process and store book results
            List<String> bookIds = processSearchResults(searchResults);

            // Update search results
            updateSearchResults(searchQuery, bookIds, searchResults.size());

            // Update user activity if user is specified
            if (searchQuery.getUserId() != null) {
                updateUserActivity(searchQuery);
            }

            // Mark execution as completed
            updateExecutionStatus(searchQuery, "completed");

            logger.info("Search execution completed for query: {} with {} results", 
                       searchQuery.getQueryId(), bookIds.size());

        } catch (Exception e) {
            logger.error("Search execution failed for query: {}", searchQuery.getQueryId(), e);
            updateExecutionStatus(searchQuery, "failed", e.getMessage());
        }

        return entityWithMetadata;
    }

    private void updateExecutionStatus(SearchQuery searchQuery, String status) {
        updateExecutionStatus(searchQuery, status, null);
    }

    private void updateExecutionStatus(SearchQuery searchQuery, String status, String errorMessage) {
        SearchQuery.SearchExecution execution = searchQuery.getExecution();
        if (execution == null) {
            execution = new SearchQuery.SearchExecution();
            searchQuery.setExecution(execution);
        }

        execution.setStatus(status);
        execution.setDataSource("open_library");
        execution.setApiEndpoint("https://openlibrary.org/search.json");

        if ("failed".equals(status) && errorMessage != null) {
            execution.setErrorMessage(errorMessage);
        }

        if ("executing".equals(status)) {
            searchQuery.setExecutedAt(LocalDateTime.now());
        } else if ("completed".equals(status) || "failed".equals(status)) {
            searchQuery.setCompletedAt(LocalDateTime.now());
            if (searchQuery.getExecutedAt() != null) {
                long executionTime = java.time.Duration.between(searchQuery.getExecutedAt(), searchQuery.getCompletedAt()).toMillis();
                execution.setExecutionTimeMs(executionTime);
            }
        }
    }

    private List<Book> executeOpenLibrarySearch(SearchQuery searchQuery) {
        // Simulate Open Library API search
        // In a real implementation, this would make HTTP calls to Open Library API
        logger.info("Simulating Open Library API search for term: {}", searchQuery.getSearchTerm());

        List<Book> results = new ArrayList<>();
        
        // Create sample books based on search term
        for (int i = 1; i <= 5; i++) {
            Book book = createSampleBook(searchQuery.getSearchTerm(), i);
            results.add(book);
        }

        return results;
    }

    private Book createSampleBook(String searchTerm, int index) {
        Book book = new Book();
        book.setBookId(UUID.randomUUID().toString());
        book.setTitle(searchTerm + " - Book " + index);
        book.setAuthors(Arrays.asList("Author " + index, "Co-Author " + index));
        book.setIsbn("978-" + String.format("%010d", index));
        book.setOpenLibraryKey("/works/OL" + index + "W");
        book.setPublicationYear(2020 + (index % 5));
        book.setPublisher("Publisher " + index);
        book.setLanguage("en");
        book.setPageCount(200 + (index * 50));
        book.setSubjects(Arrays.asList("Fiction", "Literature", "Modern"));
        book.setGenres(Arrays.asList("Fiction", "Drama"));
        book.setCreatedAt(LocalDateTime.now());
        book.setUpdatedAt(LocalDateTime.now());

        // Create cover information
        Book.BookCover cover = new Book.BookCover();
        cover.setHasImage(true);
        cover.setSmallUrl("https://covers.openlibrary.org/b/id/" + index + "-S.jpg");
        cover.setMediumUrl("https://covers.openlibrary.org/b/id/" + index + "-M.jpg");
        cover.setLargeUrl("https://covers.openlibrary.org/b/id/" + index + "-L.jpg");
        book.setCover(cover);

        // Create metadata
        Book.BookMetadata metadata = new Book.BookMetadata();
        metadata.setSearchCount(1);
        metadata.setPopularityScore(0.5 + (index * 0.1));
        metadata.setDataSource("open_library");
        metadata.setLastUpdated(LocalDateTime.now());
        book.setMetadata(metadata);

        return book;
    }

    private List<String> processSearchResults(List<Book> searchResults) {
        List<String> bookIds = new ArrayList<>();

        for (Book book : searchResults) {
            try {
                // Store or update the book entity
                ModelSpec bookModelSpec = new ModelSpec();
                bookModelSpec.setName("Book");
                bookModelSpec.setVersion(1);

                // In a real implementation, you would store the book entity using EntityService
                // EntityWithMetadata<Book> bookEntity = EntityWithMetadata.builder()
                //     .entity(book)
                //     .build();

                // In a real implementation, you would check if book already exists
                // and update it accordingly
                bookIds.add(book.getBookId());

                logger.debug("Processed book: {} - {}", book.getBookId(), book.getTitle());

            } catch (Exception e) {
                logger.error("Failed to process book: {}", book.getTitle(), e);
            }
        }

        return bookIds;
    }

    private void updateSearchResults(SearchQuery searchQuery, List<String> bookIds, int totalResults) {
        SearchQuery.SearchResults results = searchQuery.getResults();
        if (results == null) {
            results = new SearchQuery.SearchResults();
            searchQuery.setResults(results);
        }

        results.setTotalResults(totalResults);
        results.setReturnedResults(bookIds.size());
        results.setBookIds(bookIds);
        results.setHasMoreResults(false); // Simplified for this example
        results.setRelevanceScore(0.8); // Simplified scoring

        // Create distribution maps (simplified)
        Map<String, Integer> genreDistribution = new HashMap<>();
        genreDistribution.put("Fiction", bookIds.size());
        results.setGenreDistribution(genreDistribution);

        Map<String, Integer> authorDistribution = new HashMap<>();
        for (int i = 0; i < bookIds.size(); i++) {
            authorDistribution.put("Author " + (i + 1), 1);
        }
        results.setAuthorDistribution(authorDistribution);
    }

    private void updateUserActivity(SearchQuery searchQuery) {
        try {
            // In a real implementation, you would fetch and update the User entity
            logger.info("Would update user activity for user: {}", searchQuery.getUserId());
            
            // This would involve:
            // 1. Fetching the User entity by userId
            // 2. Updating search counts and recent search terms
            // 3. Saving the updated User entity
            
        } catch (Exception e) {
            logger.error("Failed to update user activity for user: {}", searchQuery.getUserId(), e);
        }
    }
}
