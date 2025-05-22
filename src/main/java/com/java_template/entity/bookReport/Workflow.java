package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

public class Workflow {
    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public CompletableFuture<ObjectNode> processBookReport(ObjectNode reportEntity) {
        return processAddGeneratedAt(reportEntity)
                .thenCompose(this::processFetchBooks)
                .thenCompose(this::processAnalyzeBooks)
                .thenCompose(this::processSetReportFields)
                .thenCompose(this::processSendEmail)
                .exceptionally(ex -> {
                    logger.error("Error in processBookReport workflow: {}", ex.getMessage(), ex);
                    return reportEntity;
                });
    }

    private CompletableFuture<ObjectNode> processAddGeneratedAt(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("generatedAt", Instant.now().toString());
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processFetchBooks(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = new URI("https://fakerestapi.azurewebsites.net/api/v1/Books");
                String response = restTemplate.getForObject(uri, String.class);
                if (response == null || response.isEmpty()) {
                    logger.error("Empty response from external books API");
                    entity.putArray("books").removeAll();
                    return entity;
                }
                JsonNode root = objectMapper.readTree(response);
                if (!root.isArray()) {
                    logger.error("Unexpected books API response format");
                    entity.putArray("books").removeAll();
                    return entity;
                }
                ArrayNode booksArray = objectMapper.createArrayNode();
                root.forEach(booksArray::add);
                entity.set("books", booksArray);
            } catch (Exception e) {
                logger.error("Failed to fetch books: {}", e.getMessage(), e);
                entity.putArray("books").removeAll();
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAnalyzeBooks(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode booksNode = entity.get("books");
                if (booksNode == null || !booksNode.isArray()) {
                    return entity;
                }
                List<ObjectNode> books = new ArrayList<>();
                int totalPageCount = 0;
                LocalDate earliestDate = null;
                LocalDate latestDate = null;

                for (JsonNode node : booksNode) {
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
                entity.put("totalBooks", books.size());
                entity.put("totalPageCount", totalPageCount);

                ObjectNode pubDateRange = objectMapper.createObjectNode();
                if (earliestDate != null)
                    pubDateRange.put("earliest", earliestDate.toString());
                else
                    pubDateRange.putNull("earliest");
                if (latestDate != null)
                    pubDateRange.put("latest", latestDate.toString());
                else
                    pubDateRange.putNull("latest");
                entity.set("publicationDateRange", pubDateRange);

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
                entity.set("popularTitles", popularTitles);
            } catch (Exception e) {
                logger.error("Error analyzing books: {}", e.getMessage(), e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processSetReportFields(ObjectNode entity) {
        // No additional processing needed here since fields are set in analyze step
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSendEmail(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            sendAsyncReportEmail(entity);
            return entity;
        });
    }

    private void sendAsyncReportEmail(ObjectNode reportEntity) {
        // implementation omitted for brevity
    }

    private String snippet(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}