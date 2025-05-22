package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/books")
    public CompletableFuture<UUID> addBook(@Valid @RequestBody ObjectNode bookEntity) {
        return entityService.addItem("book", ENTITY_VERSION, bookEntity, this::processBook);
    }

    @PostMapping("/books/batch")
    public CompletableFuture<List<UUID>> addBooks(@Valid @RequestBody ArrayNode bookEntities) {
        // Apply workflow function to each entity before batch add
        List<ObjectNode> processedEntities = new ArrayList<>();
        List<CompletableFuture<ObjectNode>> futures = new ArrayList<>();
        for (JsonNode node : bookEntities) {
            if (node instanceof ObjectNode) {
                futures.add(processBook((ObjectNode) node));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    for (CompletableFuture<ObjectNode> future : futures) {
                        try {
                            processedEntities.add(future.get());
                        } catch (Exception e) {
                            logger.error("Error processing book in batch: {}", e.getMessage(), e);
                        }
                    }
                    return processedEntities;
                })
                .thenCompose(entities -> entityService.addItems("book", ENTITY_VERSION, entities));
    }

    @GetMapping(value = "/books/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ObjectNode> getBook(@PathVariable("id") UUID id) {
        return entityService.getItem("book", ENTITY_VERSION, id)
                .thenApply(entity -> {
                    if (entity == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
                    }
                    ((ObjectNode) entity).remove("technicalId");
                    return (ObjectNode) entity;
                });
    }

    @GetMapping(value = "/books", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ArrayNode> getAllBooks() {
        return entityService.getItems("book", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    for (JsonNode node : arrayNode) {
                        if (node instanceof ObjectNode) {
                            ((ObjectNode) node).remove("technicalId");
                        }
                    }
                    return arrayNode;
                });
    }

    @PutMapping("/books/{id}")
    public CompletableFuture<UUID> updateBook(@PathVariable("id") UUID id, @Valid @RequestBody ObjectNode bookEntity) {
        // No workflow function on update to prevent recursion
        return entityService.updateItem("book", ENTITY_VERSION, id, bookEntity);
    }

    @DeleteMapping("/books/{id}")
    public CompletableFuture<UUID> deleteBook(@PathVariable("id") UUID id) {
        return entityService.deleteItem("book", ENTITY_VERSION, id);
    }

    // Workflow function for Book entity - modifies entity asynchronously before persistence
    private CompletableFuture<ObjectNode> processBook(ObjectNode bookEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Trim title if present
                if (bookEntity.hasNonNull("title")) {
                    String title = bookEntity.get("title").asText();
                    bookEntity.put("title", title.trim());
                }

                // Add processedAt timestamp
                bookEntity.put("processedAt", Instant.now().toString());

                // Enrich entity with pageCount validation or normalization if required
                if (bookEntity.has("pageCount")) {
                    int pageCount = bookEntity.get("pageCount").asInt(-1);
                    if (pageCount < 0) {
                        bookEntity.put("pageCount", 0);
                    }
                }

                // Example of adding a supplementary entity of different model asynchronously
                // Here we simulate a scenario - do not call add/update/delete on same model to avoid recursion
                // entityService.addItem("auditLog", ENTITY_VERSION, createAuditLogEntity(bookEntity), null);

                // Fire and forget async audit log
                sendAsyncAuditLog(bookEntity);

                return bookEntity;
            } catch (Exception e) {
                logger.error("Error in processBook workflow: {}", e.getMessage(), e);
                return bookEntity; // Return original entity on error to prevent blocking persistence
            }
        });
    }

    private void sendAsyncAuditLog(ObjectNode entity) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Audit log for book entity: {}", entity);
                // Place email sending or other notification code here if needed
            } catch (Exception e) {
                logger.error("Failed to send audit log async: {}", e.getMessage(), e);
            }
        });
    }

    // The following is moved logic from previous report generation async task
    // Now implemented as a workflow function for a hypothetical 'bookReport' entity model

    @PostMapping("/report")
    public CompletableFuture<UUID> addReport(@Valid @RequestBody ObjectNode reportEntity) {
        return entityService.addItem("bookReport", ENTITY_VERSION, reportEntity, this::processBookReport);
    }

    @GetMapping(value = "/report/{reportId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ObjectNode> getReport(@PathVariable("reportId") @NotBlank String reportId) {
        return entityService.getItem("bookReport", ENTITY_VERSION, reportId)
                .thenApply(entity -> {
                    if (entity == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
                    }
                    ((ObjectNode) entity).remove("technicalId");
                    return (ObjectNode) entity;
                });
    }

    // Workflow function for bookReport entity - generates report asynchronously before persistence
    private CompletableFuture<ObjectNode> processBookReport(ObjectNode reportEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add generatedAt timestamp
                reportEntity.put("generatedAt", Instant.now().toString());

                // Fetch books from external API
                URI uri = new URI("https://fakerestapi.azurewebsites.net/api/v1/Books");
                String response = restTemplate.getForObject(uri, String.class);
                if (response == null || response.isEmpty()) {
                    logger.error("Empty response from external books API");
                    return reportEntity;
                }
                JsonNode root = objectMapper.readTree(response);
                if (!root.isArray()) {
                    logger.error("Unexpected books API response format");
                    return reportEntity;
                }

                List<ObjectNode> books = new ArrayList<>();
                int totalPageCount = 0;
                LocalDate earliestDate = null;
                LocalDate latestDate = null;

                for (JsonNode node : root) {
                    ObjectNode bookNode = (ObjectNode) node;
                    books.add(bookNode);

                    int pageCount = bookNode.path("pageCount").asInt(0);
                    totalPageCount += pageCount;

                    String publishDateStr = bookNode.path("publishDate").asText(null);
                    if (publishDateStr != null && !publishDateStr.isEmpty()) {
                        LocalDate publishDate = null;
                        try {
                            publishDate = LocalDate.parse(publishDateStr.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
                        } catch (Exception ex) {
                            logger.warn("Invalid publishDate format: {}", publishDateStr);
                        }
                        if (publishDate != null) {
                            if (earliestDate == null || publishDate.isBefore(earliestDate)) {
                                earliestDate = publishDate;
                            }
                            if (latestDate == null || publishDate.isAfter(latestDate)) {
                                latestDate = publishDate;
                            }
                        }
                    }
                }

                // Populate report fields
                reportEntity.put("totalBooks", books.size());
                reportEntity.put("totalPageCount", totalPageCount);

                ObjectNode pubDateRange = objectMapper.createObjectNode();
                if (earliestDate != null)
                    pubDateRange.put("earliest", earliestDate.toString());
                else
                    pubDateRange.putNull("earliest");
                if (latestDate != null)
                    pubDateRange.put("latest", latestDate.toString());
                else
                    pubDateRange.putNull("latest");
                reportEntity.set("publicationDateRange", pubDateRange);

                // Compute top 5 popular titles by pageCount
                List<ObjectNode> topBooks = books.stream()
                        .sorted((a, b) -> Integer.compare(b.path("pageCount").asInt(0), a.path("pageCount").asInt(0)))
                        .limit(5)
                        .collect(Collectors.toList());

                ArrayNode popularTitles = objectMapper.createArrayNode();
                for (ObjectNode book : topBooks) {
                    ObjectNode popular = objectMapper.createObjectNode();
                    popular.set("id", book.get("id"));
                    popular.set("title", book.get("title"));
                    popular.put("descriptionSnippet", snippet(book.path("description").asText(null), 150));
                    popular.put("excerptSnippet", snippet(book.path("excerpt").asText(null), 150));
                    popular.set("pageCount", book.get("pageCount"));
                    popular.set("publishDate", book.get("publishDate"));
                    popularTitles.add(popular);
                }
                reportEntity.set("popularTitles", popularTitles);

                // Fire and forget email notification after report is ready
                sendAsyncReportEmail(reportEntity);

                return reportEntity;
            } catch (Exception e) {
                logger.error("Error in processBookReport workflow: {}", e.getMessage(), e);
                return reportEntity;
            }
        });
    }

    private void sendAsyncReportEmail(ObjectNode reportEntity) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Sending report email for report: {}", reportEntity);
                // Implement email sending integration here
            } catch (Exception e) {
                logger.error("Failed to send report email async: {}", e.getMessage(), e);
            }
        });
    }

    private String snippet(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
        return Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getReason()
        );
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return Map.of(
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Internal server error"
        );
    }
}