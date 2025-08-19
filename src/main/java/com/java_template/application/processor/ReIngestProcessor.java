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

@Component
public class ReIngestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReIngestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReIngestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Book reingest for request: {}", request.getId());

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
            logger.info("Attempting reingest for book id={}", book.getOpenLibraryId());
            // Simple reingest logic: update lastIngestedAt and touch metadata
            book.setLastIngestedAt(Instant.now().toString());
            // Could reset summary/genres if missing; keep idempotent behavior
        } catch (Exception ex) {
            logger.error("Error during reingest for book id={}: {}", book != null ? book.getOpenLibraryId() : null, ex.getMessage(), ex);
        }
        return book;
    }
}
