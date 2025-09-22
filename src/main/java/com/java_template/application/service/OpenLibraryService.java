package com.java_template.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.book.version_1.Book;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenLibraryService - Service for integrating with Open Library API
 * 
 * This service handles:
 * - Searching books using Open Library Search API
 * - Fetching book details and metadata
 * - Rate limiting and error handling
 * - Data transformation from API response to Book entities
 */
@Service
public class OpenLibraryService {

    private static final Logger logger = LoggerFactory.getLogger(OpenLibraryService.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${openlibrary.api.base-url:https://openlibrary.org}")
    private String baseUrl;
    
    @Value("${openlibrary.api.timeout:10000}")
    private int timeoutMs;
    
    @Value("${openlibrary.api.rate-limit:100}")
    private int rateLimitPerMinute;
    
    // Simple rate limiting - in production, use Redis or more sophisticated solution
    private long lastRequestTime = 0;
    private final long minRequestInterval;

    public OpenLibraryService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.minRequestInterval = 60000 / rateLimitPerMinute; // milliseconds between requests
    }

    /**
     * Search for books using Open Library Search API
     */
    public CompletableFuture<List<Book>> searchBooks(String query, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Apply rate limiting
                applyRateLimit();
                
                // Build search URL
                String searchUrl = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search.json")
                        .queryParam("q", query)
                        .queryParam("limit", Math.min(limit, 100)) // API limit
                        .queryParam("fields", "key,title,author_name,first_publish_year,isbn,subject,cover_i,publisher")
                        .build()
                        .toUriString();

                logger.info("Searching Open Library with query: {} (limit: {})", query, limit);
                
                // Make API call
                ResponseEntity<OpenLibrarySearchResponse> response = restTemplate.getForEntity(
                    searchUrl, OpenLibrarySearchResponse.class);

                if (response.getBody() != null) {
                    List<Book> books = transformSearchResults(response.getBody());
                    logger.info("Found {} books from Open Library for query: {}", books.size(), query);
                    return books;
                } else {
                    logger.warn("Empty response from Open Library for query: {}", query);
                    return new ArrayList<>();
                }

            } catch (Exception e) {
                logger.error("Failed to search Open Library for query: {}", query, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Search books with advanced filters
     */
    public CompletableFuture<List<Book>> searchBooksWithFilters(SearchFilters filters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Apply rate limiting
                applyRateLimit();
                
                // Build advanced search query
                String query = buildAdvancedQuery(filters);
                
                // Build search URL
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search.json")
                        .queryParam("q", query)
                        .queryParam("limit", Math.min(filters.getLimit() != null ? filters.getLimit() : 20, 100))
                        .queryParam("fields", "key,title,author_name,first_publish_year,isbn,subject,cover_i,publisher");

                if (filters.getOffset() != null && filters.getOffset() > 0) {
                    builder.queryParam("offset", filters.getOffset());
                }

                String searchUrl = builder.build().toUriString();

                logger.info("Advanced search Open Library with filters: {}", filters);
                
                // Make API call
                ResponseEntity<OpenLibrarySearchResponse> response = restTemplate.getForEntity(
                    searchUrl, OpenLibrarySearchResponse.class);

                if (response.getBody() != null) {
                    List<Book> books = transformSearchResults(response.getBody());
                    logger.info("Found {} books from Open Library with filters", books.size());
                    return books;
                } else {
                    logger.warn("Empty response from Open Library with filters");
                    return new ArrayList<>();
                }

            } catch (Exception e) {
                logger.error("Failed to search Open Library with filters: {}", filters, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Get book details by Open Library key
     */
    public CompletableFuture<Book> getBookByKey(String openLibraryKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Apply rate limiting
                applyRateLimit();
                
                String detailUrl = baseUrl + openLibraryKey + ".json";
                
                logger.info("Fetching book details from Open Library: {}", openLibraryKey);
                
                ResponseEntity<OpenLibraryBookDetail> response = restTemplate.getForEntity(
                    detailUrl, OpenLibraryBookDetail.class);

                if (response.getBody() != null) {
                    Book book = transformBookDetail(response.getBody(), openLibraryKey);
                    logger.info("Retrieved book details for: {}", book.getTitle());
                    return book;
                } else {
                    logger.warn("Empty response for book key: {}", openLibraryKey);
                    return null;
                }

            } catch (Exception e) {
                logger.error("Failed to get book details for key: {}", openLibraryKey, e);
                return null;
            }
        });
    }

    private void applyRateLimit() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        
        if (timeSinceLastRequest < minRequestInterval) {
            try {
                long sleepTime = minRequestInterval - timeSinceLastRequest;
                logger.debug("Rate limiting: sleeping for {} ms", sleepTime);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Rate limiting sleep interrupted", e);
            }
        }
        
        lastRequestTime = System.currentTimeMillis();
    }

    private String buildAdvancedQuery(SearchFilters filters) {
        List<String> queryParts = new ArrayList<>();
        
        if (filters.getTitle() != null && !filters.getTitle().trim().isEmpty()) {
            queryParts.add("title:" + filters.getTitle());
        }
        
        if (filters.getAuthor() != null && !filters.getAuthor().trim().isEmpty()) {
            queryParts.add("author:" + filters.getAuthor());
        }
        
        if (filters.getSubject() != null && !filters.getSubject().trim().isEmpty()) {
            queryParts.add("subject:" + filters.getSubject());
        }
        
        if (filters.getPublisher() != null && !filters.getPublisher().trim().isEmpty()) {
            queryParts.add("publisher:" + filters.getPublisher());
        }
        
        if (filters.getLanguage() != null && !filters.getLanguage().trim().isEmpty()) {
            queryParts.add("language:" + filters.getLanguage());
        }
        
        // If no specific fields, use general query
        if (queryParts.isEmpty() && filters.getGeneralQuery() != null && !filters.getGeneralQuery().trim().isEmpty()) {
            return filters.getGeneralQuery();
        }
        
        return String.join(" AND ", queryParts);
    }

    private List<Book> transformSearchResults(OpenLibrarySearchResponse response) {
        List<Book> books = new ArrayList<>();
        
        if (response.getDocs() != null) {
            for (OpenLibraryDoc doc : response.getDocs()) {
                try {
                    Book book = transformDocToBook(doc);
                    books.add(book);
                } catch (Exception e) {
                    logger.warn("Failed to transform search result doc: {}", doc.getKey(), e);
                }
            }
        }
        
        return books;
    }

    private Book transformDocToBook(OpenLibraryDoc doc) {
        Book book = new Book();
        
        // Basic information
        book.setBookId(UUID.randomUUID().toString());
        book.setTitle(doc.getTitle());
        book.setAuthors(doc.getAuthorName() != null ? doc.getAuthorName() : new ArrayList<>());
        book.setOpenLibraryKey(doc.getKey());
        book.setPublicationYear(doc.getFirstPublishYear());
        book.setSubjects(doc.getSubject() != null ? doc.getSubject() : new ArrayList<>());
        
        // ISBN (take first one if available)
        if (doc.getIsbn() != null && !doc.getIsbn().isEmpty()) {
            book.setIsbn(doc.getIsbn().get(0));
        }
        
        // Publisher (take first one if available)
        if (doc.getPublisher() != null && !doc.getPublisher().isEmpty()) {
            book.setPublisher(doc.getPublisher().get(0));
        }
        
        // Cover information
        if (doc.getCoverId() != null) {
            Book.BookCover cover = new Book.BookCover();
            cover.setHasImage(true);
            cover.setSmallUrl("https://covers.openlibrary.org/b/id/" + doc.getCoverId() + "-S.jpg");
            cover.setMediumUrl("https://covers.openlibrary.org/b/id/" + doc.getCoverId() + "-M.jpg");
            cover.setLargeUrl("https://covers.openlibrary.org/b/id/" + doc.getCoverId() + "-L.jpg");
            book.setCover(cover);
        }
        
        // Metadata
        Book.BookMetadata metadata = new Book.BookMetadata();
        metadata.setSearchCount(0);
        metadata.setPopularityScore(0.5);
        metadata.setDataSource("open_library");
        metadata.setLastUpdated(LocalDateTime.now());
        book.setMetadata(metadata);
        
        // Timestamps
        book.setCreatedAt(LocalDateTime.now());
        book.setUpdatedAt(LocalDateTime.now());
        
        return book;
    }

    private Book transformBookDetail(OpenLibraryBookDetail detail, String openLibraryKey) {
        Book book = new Book();
        
        // Basic information
        book.setBookId(UUID.randomUUID().toString());
        book.setTitle(detail.getTitle());
        book.setOpenLibraryKey(openLibraryKey);
        
        // Authors (simplified - in real implementation would resolve author keys)
        if (detail.getAuthors() != null && !detail.getAuthors().isEmpty()) {
            List<String> authorNames = new ArrayList<>();
            for (OpenLibraryAuthor author : detail.getAuthors()) {
                authorNames.add("Author " + author.getKey()); // Simplified
            }
            book.setAuthors(authorNames);
        }
        
        // Other details
        book.setPublisher(detail.getPublishers() != null && !detail.getPublishers().isEmpty() ? 
                         detail.getPublishers().get(0) : null);
        book.setSubjects(detail.getSubjects() != null ? detail.getSubjects() : new ArrayList<>());
        
        // Metadata
        Book.BookMetadata metadata = new Book.BookMetadata();
        metadata.setDataSource("open_library");
        metadata.setLastUpdated(LocalDateTime.now());
        book.setMetadata(metadata);
        
        // Timestamps
        book.setCreatedAt(LocalDateTime.now());
        book.setUpdatedAt(LocalDateTime.now());
        
        return book;
    }

    // DTOs for Open Library API responses
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenLibrarySearchResponse {
        @JsonProperty("numFound")
        private Integer numFound;
        
        @JsonProperty("start")
        private Integer start;
        
        @JsonProperty("docs")
        private List<OpenLibraryDoc> docs;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenLibraryDoc {
        @JsonProperty("key")
        private String key;
        
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("author_name")
        private List<String> authorName;
        
        @JsonProperty("first_publish_year")
        private Integer firstPublishYear;
        
        @JsonProperty("isbn")
        private List<String> isbn;
        
        @JsonProperty("subject")
        private List<String> subject;
        
        @JsonProperty("cover_i")
        private Integer coverId;
        
        @JsonProperty("publisher")
        private List<String> publisher;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenLibraryBookDetail {
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("authors")
        private List<OpenLibraryAuthor> authors;
        
        @JsonProperty("publishers")
        private List<String> publishers;
        
        @JsonProperty("subjects")
        private List<String> subjects;
        
        @JsonProperty("description")
        private Object description; // Can be string or object
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenLibraryAuthor {
        @JsonProperty("key")
        private String key;
    }

    @Data
    public static class SearchFilters {
        private String generalQuery;
        private String title;
        private String author;
        private String subject;
        private String publisher;
        private String language;
        private Integer limit;
        private Integer offset;
    }
}
