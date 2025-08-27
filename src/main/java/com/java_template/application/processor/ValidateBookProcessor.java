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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ValidateBookProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateBookProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateBookProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Book for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Book.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Book entity) {
        // Structural validation relies on entity.isValid() (uses available getters)
        // Do not block processing on publishDate parseability here so processor can
        // mark the entity for review or set safe defaults as needed.
        return entity != null && entity.isValid();
    }

    private Book processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Book> context) {
        Book book = context.entity();

        // Defensive: ensure book is not null
        if (book == null) {
            logger.warn("Received null book in processing context");
            return null;
        }

        // Validate publishDate parseability; if it fails, set review-indicative defaults.
        try {
            LocalDate.parse(book.getPublishDate());
            // If parse succeeds, ensure derived flags are initialized safely for downstream processors
            if (book.getPopularityScore() == null) {
                // leave actual scoring to EnrichMetadataProcessor; initialize to 0.0 to avoid NaN issues
                book.setPopularityScore(0.0);
            } else {
                // ensure non-negative and not NaN
                Double score = book.getPopularityScore();
                if (score.isNaN() || score < 0.0) {
                    book.setPopularityScore(0.0);
                }
            }
            if (book.getIsPopular() == null) {
                book.setIsPopular(false);
            }
            // Ensure fetchTimestamp exists so downstream processors have a reference point
            if (book.getFetchTimestamp() == null || book.getFetchTimestamp().isBlank()) {
                book.setFetchTimestamp(Instant.now().toString());
            }
        } catch (DateTimeParseException | NullPointerException e) {
            // publishDate invalid -> mark for review by setting safe defaults and ensuring fetchTimestamp exists
            logger.warn("Book validation failed (publishDate invalid) for id {}: {}", book.getId(), e.getMessage());
            // ensure fetchTimestamp exists so entity remains transitively valid for the system to persist and route
            if (book.getFetchTimestamp() == null || book.getFetchTimestamp().isBlank()) {
                book.setFetchTimestamp(Instant.now().toString());
            }
            // set derived fields to safe review-indicative defaults
            book.setPopularityScore(0.0);
            book.setIsPopular(false);
            // Note: Book entity has no explicit 'state' field; we avoid inventing fields.
            // Downstream systems / UIs should treat books with defaulted derived values
            // and an invalid publishDate as items needing manual review.
        }

        // Additional lightweight sanity adjustments:
        if (book.getPageCount() != null && book.getPageCount() < 0) {
            // normalize negative page counts to zero to avoid downstream errors
            logger.warn("Normalizing negative pageCount for book id {}: {}", book.getId(), book.getPageCount());
            book.setPageCount(0);
        }

        return book;
    }
}