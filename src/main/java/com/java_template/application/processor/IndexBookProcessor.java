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

@Component
public class IndexBookProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexBookProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

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
            // Simulate indexing logic (e.g., call to search index service)
            logger.info("Indexing book id={} title={}", book.getOpenLibraryId(), book.getTitle());

            // In a real implementation we would call an index service. Here we simulate success and set state.
            // Ensure genres is not null for indexing
            if (book.getGenres() == null) {
                book.setGenres(java.util.Collections.emptyList());
            }
            // Assign state by setting a pseudo-field in metadata; using summary as placeholder is not allowed.
            // The Book entity in this prototype does not have an explicit 'state' field. We will use lastIngestedAt update to indicate activity.
            book.setLastIngestedAt(java.time.Instant.now().toString());

            logger.info("Indexing simulated complete for book id={}", book.getOpenLibraryId());
        } catch (Exception ex) {
            logger.error("Error during indexing for book id={}: {}", book != null ? book.getOpenLibraryId() : null, ex.getMessage(), ex);
            // Do not propagate, let workflow handle retries
        }
        return book;
    }
}
