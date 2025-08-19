package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class EnrichMetadataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichMetadataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EnrichMetadataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Book enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Book.class)
            .validate(this::isValidEntity, "Invalid book entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Book book) {
        return book != null && book.isValid();
    }

    private Book processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Book> context) {
        Book book = context.entity();
        try {
            logger.info("Enriching metadata for book id={}", book.getOpenLibraryId());
            // Call Open Library API to fetch metadata
            JsonNode ext = fetchOpenLibraryData(book.getOpenLibraryId());
            if (ext != null) {
                // Cover
                if ((book.getCoverImageUrl() == null || book.getCoverImageUrl().isBlank())) {
                    String cover = null;
                    if (ext.has("cover") && ext.get("cover").has("large")) {
                        cover = ext.get("cover").get("large").asText(null);
                    }
                    if (cover == null && ext.has("cover") && ext.get("cover").has("medium")) {
                        cover = ext.get("cover").get("medium").asText(null);
                    }
                    if (cover != null && !cover.isBlank()) {
                        book.setCoverImageUrl(cover);
                    } else {
                        // fallback predictable URL
                        book.setCoverImageUrl("https://covers.openlibrary.org/b/olid/" + book.getOpenLibraryId() + "-L.jpg");
                    }
                }

                // Genres/subjects
                Set<String> merged = new HashSet<>();
                if (book.getGenres() != null) {
                    merged.addAll(book.getGenres());
                }
                if (ext.has("subjects") && ext.get("subjects").isArray()) {
                    for (JsonNode s : ext.get("subjects")) {
                        if (s.has("name")) merged.add(s.get("name").asText());
                        else merged.add(s.asText());
                    }
                }
                if (!merged.isEmpty()) {
                    book.setGenres(new ArrayList<>(merged));
                } else {
                    // fallback heuristic from title
                    List<String> existingGenres = book.getGenres() == null ? new ArrayList<>() : book.getGenres();
                    if (existingGenres.isEmpty() && book.getTitle() != null) {
                        String titleLower = book.getTitle().toLowerCase();
                        if (titleLower.contains("history")) {
                            existingGenres.add("History");
                        } else if (titleLower.contains("science")) {
                            existingGenres.add("Science");
                        } else {
                            existingGenres.add("General");
                        }
                        book.setGenres(existingGenres);
                    }
                }

                // Summary/description
                if ((book.getSummary() == null || book.getSummary().isBlank())) {
                    String summary = null;
                    if (ext.has("description")) {
                        JsonNode desc = ext.get("description");
                        if (desc.isTextual()) summary = desc.asText();
                        else if (desc.has("value")) summary = desc.get("value").asText(null);
                    }
                    if (summary != null && !summary.isBlank()) {
                        book.setSummary(summary);
                    } else {
                        // generate lightweight summary
                        StringBuilder sb = new StringBuilder();
                        sb.append("A book titled '").append(book.getTitle()).append("' by ");
                        if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                            sb.append(String.join(", ", book.getAuthors()));
                        } else {
                            sb.append("unknown author");
                        }
                        sb.append(". This record was enriched by the metadata pipeline.");
                        book.setSummary(sb.toString());
                    }
                }

                // Publication year attempt
                if ((book.getPublicationYear() == null) && ext.has("publish_date")) {
                    String pd = ext.get("publish_date").asText(null);
                    if (pd != null) {
                        try {
                            // try to extract year from string
                            String digits = pd.replaceAll("[^0-9]", "");
                            if (digits.length() >= 4) {
                                int year = Integer.parseInt(digits.substring(0, 4));
                                book.setPublicationYear(year);
                            }
                        } catch (Exception e) {
                            logger.debug("Could not parse publish_date='{}' for book id={}", pd, book.getOpenLibraryId());
                        }
                    }
                }

            } else {
                logger.warn("No external metadata found for book id={}", book.getOpenLibraryId());
            }

            // Always update last ingested timestamp when enrichment attempted
            book.setLastIngestedAt(Instant.now().toString());

            logger.info("Enrichment completed for book id={}", book.getOpenLibraryId());
        } catch (Exception ex) {
            logger.error("Error during enrichment for book id={}: {}", book != null ? book.getOpenLibraryId() : null, ex.getMessage(), ex);
        }
        return book;
    }

    private JsonNode fetchOpenLibraryData(String olid) {
        if (olid == null || olid.isBlank()) return null;
        String url = "https://openlibrary.org/api/books?bibkeys=OLID:" + olid + "&format=json&jscmd=data";
        int attempts = 0;
        while (attempts < 3) {
            attempts++;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                if (status >= 200 && status < 300) {
                    String body = resp.body();
                    JsonNode root = objectMapper.readTree(body);
                    String key = "OLID:" + olid;
                    if (root.has(key)) {
                        return root.get(key);
                    } else {
                        return null;
                    }
                } else if (status >= 500) {
                    // server error - retry
                    logger.warn("OpenLibrary returned {} for olid={} attempt={}", status, olid, attempts);
                    Thread.sleep(250L * attempts);
                    continue;
                } else {
                    logger.warn("OpenLibrary returned non-retryable status {} for olid={}: body={}", status, olid, resp.body());
                    return null;
                }
            } catch (IOException | InterruptedException ex) {
                logger.warn("Transient error fetching OpenLibrary data for olid={} attempt={} err={}", olid, attempts, ex.getMessage());
                try { Thread.sleep(200L * attempts); } catch (InterruptedException ignored) {}
            }
        }
        logger.error("Failed to fetch OpenLibrary data for olid={} after retries", olid);
        return null;
    }
}
