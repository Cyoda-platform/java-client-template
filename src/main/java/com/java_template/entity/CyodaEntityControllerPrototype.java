```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda/api")
@Slf4j
@Validated
public class CyodaEntityControllerPrototype {

    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.objectMapper = new ObjectMapper();
        this.entityService = entityService;
    }

    @PostMapping("/books/search")
    public List<Book> searchBooks(@RequestBody @Valid SearchRequest searchRequest) {
        log.info("Searching books with query: {}", searchRequest.getQuery());
        String url = "https://openlibrary.org/search.json?q=" + searchRequest.getQuery();

        // Use external API for search, same logic as before
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(response);
            // TODO: Parse JSON and map to Book entities
            return List.of(); // Placeholder for parsed books
        } catch (Exception e) {
            log.error("Error searching books", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching books", e);
        }
    }

    @GetMapping("/books/{bookId}")
    public CompletableFuture<Book> getBookDetails(@PathVariable @NotBlank String bookId) {
        log.info("Fetching details for book ID: {}", bookId);

        // Fetch book details using EntityService
        return entityService.getItem("Book", ENTITY_VERSION, UUID.fromString(bookId))
                .thenApply(itemNode -> {
                    // TODO: Map ObjectNode to Book
                    return new Book(); // Placeholder for book details
                });
    }

    @PostMapping("/reports/weekly")
    public String generateWeeklyReport() {
        log.info("Generating weekly report");
        // TODO: Implement report generation logic
        return "Report URL"; // Placeholder for report URL
    }

    @PostMapping("/recommendations")
    public CompletableFuture<List<Book>> getRecommendations(@RequestBody @Valid UserRequest userRequest) {
        log.info("Fetching recommendations for user: {}", userRequest.getUserId());

        // Fetch user recommendations using EntityService
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.userId", "EQUALS", userRequest.getUserId()));

        return entityService.getItemsByCondition("Recommendation", ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    // TODO: Map ArrayNode to List<Book>
                    return List.of(); // Placeholder for recommendations
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ErrorResponse handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling error: {}", ex.getStatusCode().toString());
        return new ErrorResponse(ex.getStatusCode().value(), ex.getStatusCode().toString());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SearchRequest {
        @NotBlank
        private String query;

        @NotNull
        private Map<@NotBlank String, @NotBlank String> filters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UserRequest {
        @NotBlank
        private String userId;
    }

    @Data
    static class Book {
        private String title;
        private String author;
        private String coverImage;
        private String genre;
        private int publicationYear;
    }

    @Data
    @AllArgsConstructor
    static class ErrorResponse {
        private int status;
        private String error;
    }
}
```