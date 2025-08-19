package com.java_template.application.processor;

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
import java.util.HashMap;
import java.util.Map;

@Component
public class IndexBookProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexBookProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Indexer URL can be configured via environment variable INDEXER_URL. Default to local Elastic-style endpoint.
    private final String indexerUrl = System.getenv().getOrDefault("INDEXER_URL", "http://localhost:9200/books/_doc/");

    public IndexBookProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Book indexing for request: {}", request.getId());

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
            logger.info("Indexing book id={} title={}", book.getOpenLibraryId(), book.getTitle());

            // Ensure genres not null
            if (book.getGenres() == null) {
                book.setGenres(java.util.Collections.emptyList());
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("openLibraryId", book.getOpenLibraryId());
            payload.put("title", book.getTitle());
            payload.put("authors", book.getAuthors());
            payload.put("genres", book.getGenres());
            payload.put("publicationYear", book.getPublicationYear());

            String json = objectMapper.writeValueAsString(payload);
            String target = indexerUrl.endsWith("/") ? indexerUrl + book.getOpenLibraryId() : indexerUrl + "/" + book.getOpenLibraryId();

            int attempts = 0;
            while (attempts < 3) {
                attempts++;
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(target))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    int status = resp.statusCode();
                    if (status >= 200 && status < 300) {
                        logger.info("Indexed book id={} successfully (status={})", book.getOpenLibraryId(), status);
                        // mark activity by touching lastIngestedAt
                        book.setLastIngestedAt(Instant.now().toString());
                        break;
                    } else if (status >= 500) {
                        logger.warn("Indexer returned {} for book id={} attempt={} body={}", status, book.getOpenLibraryId(), attempts, resp.body());
                        Thread.sleep(200L * attempts);
                        continue;
                    } else {
                        logger.error("Indexer returned non-retriable status {} for book id={} body={}", status, book.getOpenLibraryId(), resp.body());
                        break;
                    }
                } catch (IOException | InterruptedException ex) {
                    logger.warn("Transient error indexing book id={} attempt={} err={}", book.getOpenLibraryId(), attempts, ex.getMessage());
                    try { Thread.sleep(150L * attempts); } catch (InterruptedException ignored) {}
                }
            }

        } catch (Exception ex) {
            logger.error("Error during indexing for book id={}: {}", book != null ? book.getOpenLibraryId() : null, ex.getMessage(), ex);
        }
        return book;
    }
}
