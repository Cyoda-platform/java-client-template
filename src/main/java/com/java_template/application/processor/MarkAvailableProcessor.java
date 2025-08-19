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
public class MarkAvailableProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkAvailableProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MarkAvailableProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Book availability for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Book.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Book book) {
        return book != null && book.getId() != null;
    }

    private Book processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Book> context) {
        Book book = context.entity();
        // mark available if valid and not excluded
        if ("ok".equalsIgnoreCase(book.getSourceStatus())) {
            // there's no 'available' field on Book, but availability is implied by sourceStatus == ok
            logger.info("MarkAvailableProcessor: book id={} marked available (sourceStatus=ok)", book.getId());
        } else {
            logger.info("MarkAvailableProcessor: book id={} not available (sourceStatus={})", book.getId(), book.getSourceStatus());
        }
        return book;
    }
}
