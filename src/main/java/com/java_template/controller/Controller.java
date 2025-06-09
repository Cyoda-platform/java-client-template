package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda/api")
@Slf4j
@Validated
public class Controller {

    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final RestTemplate restTemplate;

    public Controller(ObjectMapper objectMapper, EntityService entityService, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
        this.restTemplate = restTemplate;
    }

    @PostMapping("/books/search")
    public List<Book> searchBooks(@RequestBody @Valid SearchRequest searchRequest) {
        log.info("Searching books with query: {}", searchRequest.getQuery());
        String url = "https://openlibrary.org/search.json?q=" + searchRequest.getQuery();

        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(response);
            ArrayNode docs = (ArrayNode) rootNode.get("docs");
            List<Book> books = new ArrayList<>();
            for (JsonNode docNode : docs) {
                Book book = new Book();
                book.setTitle(docNode.path("title").asText());
                book.setAuthor(docNode.path("author_name").get(0).asText());
                book.setCoverImage(docNode.path("cover_i").asText());
                book.setGenre(docNode.path("subject").get(0).asText());
                book.setPublicationYear(docNode.path("first_publish_year").asInt());
                books.add(book);
            }
            return books;
        } catch (Exception e) {
            log.error("Error searching books", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching books", e);
        }
    }

    @GetMapping("/books/{bookId}")
    public CompletableFuture<Book> getBookDetails(@PathVariable @NotBlank String bookId) {
        log.info("Fetching details for book ID: {}", bookId);

        return entityService.getItem("Book", ENTITY_VERSION, UUID.fromString(bookId))
                .thenApply(itemNode -> {
                    Book book = new Book();
                    book.setTitle(itemNode.path("title").asText());
                    book.setAuthor(itemNode.path("author").asText());
                    book.setCoverImage(itemNode.path("coverImage").asText());
                    book.setGenre(itemNode.path("genre").asText());
                    book.setPublicationYear(itemNode.path("publicationYear").asInt());
                    return book;
                });
    }

    @PostMapping("/books")
    public CompletableFuture<UUID> addBook(@RequestBody @Valid Book book) {
        log.info("Adding new book: {}", book);

        ObjectNode bookNode = objectMapper.convertValue(book, ObjectNode.class);

        return entityService.addItem(
                "Book",
                ENTITY_VERSION,
                bookNode
        );
    }

    @PostMapping("/reports/weekly")
    public String generateWeeklyReport() {
        log.info("Generating weekly report");
        // Simulated report generation
        return "http://example.com/reports/weekly";
    }

    @PostMapping("/recommendations")
    public CompletableFuture<List<Book>> getRecommendations(@RequestBody @Valid UserRequest userRequest) {
        log.info("Fetching recommendations for user: {}", userRequest.getUserId());

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.userId", "EQUALS", userRequest.getUserId()));

        return entityService.getItemsByCondition("Recommendation", ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    List<Book> recommendations = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        Book book = new Book();
                        book.setTitle(node.path("title").asText());
                        book.setAuthor(node.path("author").asText());
                        book.setCoverImage(node.path("coverImage").asText());
                        book.setGenre(node.path("genre").asText());
                        book.setPublicationYear(node.path("publicationYear").asInt());
                        recommendations.add(book);
                    }
                    return recommendations;
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