package com.java_template.application.processor;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class EnrichMetadataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichMetadataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

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
            // Simulated external enrichment (OpenLibrary or other)
            logger.info("Enriching metadata for book id={}", book.getOpenLibraryId());

            // Populate coverImageUrl if missing using a predictable placeholder URL
            if (book.getCoverImageUrl() == null || book.getCoverImageUrl().isBlank()) {
                book.setCoverImageUrl("https://covers.openlibrary.org/b/olid/" + book.getOpenLibraryId() + "-L.jpg");
            }

            // Merge genres: if missing, provide a minimal default based on title
            List<String> existingGenres = book.getGenres();
            if (existingGenres == null) {
                existingGenres = new ArrayList<>();
            }
            if (existingGenres.isEmpty() && book.getTitle() != null) {
                // simple heuristic: if title contains "history" add History
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

            // Populate summary if missing with a light-weight generated summary
            if (book.getSummary() == null || book.getSummary().isBlank()) {
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

            // Update last ingested timestamp
            book.setLastIngestedAt(Instant.now().toString());

            logger.info("Enrichment completed for book id={}", book.getOpenLibraryId());
        } catch (Exception ex) {
            logger.error("Error during enrichment for book id={}: {}", book != null ? book.getOpenLibraryId() : null, ex.getMessage(), ex);
            // Do not throw; processors should prefer to mark state or let caller handle retries. Here we preserve entity.
        }
        return book;
    }
}
