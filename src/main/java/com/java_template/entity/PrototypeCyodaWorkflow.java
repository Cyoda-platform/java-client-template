To adhere to the new requirements and move the asynchronous logic to the `process{entity_name}` function, we need to identify parts of the endpoint logic that can be offloaded to this workflow function. This approach not only streamlines the controller logic but also leverages the asynchronous capabilities of the workflow function.

Let's consider refactoring the `addBook` endpoint so that any asynchronous tasks are moved to the `processBook` function. This function will work with the `ObjectNode` representation of the entity, allowing us to modify its state directly before it is persisted.

Here's how you might refactor the code:

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @PostMapping("/books")
    public CompletableFuture<UUID> addBook(@RequestBody @Valid Book book) {
        log.info("Adding new book: {}", book);

        // Convert the book object to an ObjectNode
        ObjectNode bookNode = objectMapper.convertValue(book, ObjectNode.class);

        return entityService.addItem(
                entityModel = "Book",
                entityVersion = ENTITY_VERSION,
                entity = bookNode,
                workflow = this::processBook
        );
    }

    private CompletableFuture<ObjectNode> processBook(ObjectNode bookNode) {
        // Example processing logic
        log.info("Processing book before persistence: {}", bookNode);

        // Asynchronous tasks can be done here
        return CompletableFuture.supplyAsync(() -> {
            // Simulate an async task, e.g., fetching additional data or performing calculations
            // Modify the bookNode directly if needed
            bookNode.put("processed", true); // Example modification

            log.info("Book processing complete");
            return bookNode;
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

### Key Changes

1. **Workflow Function as Asynchronous Task**: The `processBook` function is now performing asynchronous tasks using `CompletableFuture.supplyAsync`. This allows you to perform any necessary calculations or data fetching before persisting the entity.

2. **Direct Modification of `ObjectNode`**: The `processBook` function works directly with the `ObjectNode` type, allowing you to modify its state directly. In this example, a simple `put` operation is performed as a placeholder for potential modifications.

3. **Simplification in Controller**: By moving asynchronous logic to the `processBook` function, the controller is more focused on handling the request and response, making it cleaner and easier to maintain.

4. **Conversion to `ObjectNode`**: The conversion of the `Book` object to an `ObjectNode` before passing it to `addItem` ensures that the entity is in the correct format for processing.

This approach makes the code more modular and separates concerns effectively, allowing the controller to handle HTTP-specific tasks while the processing logic is encapsulated within the workflow function.