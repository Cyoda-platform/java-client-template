package com.java_template.application.processor;

import com.java_template.application.entity.Book;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BookProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public BookProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("BookProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Book for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Book.class)
                .withErrorHandler(this::handleBookError)
                .validate(Book::isValid, "Invalid book state")
                .map(this::applyBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "BookProcessor".equals(modelSpec.operationName()) &&
               "book".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Book applyBusinessLogic(Book book) {
        // Example business logic placeholder
        // For instance, ensure genres list is never null
        if (book.getGenres() == null) {
            book.setGenres(new ArrayList<>());
        }
        if (book.getAuthors() == null) {
            book.setAuthors(new ArrayList<>());
        }
        return book;
    }

    private ErrorInfo handleBookError(Throwable throwable, Book book) {
        logger.error("Error processing Book entity: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("BOOK_PROCESSING_ERROR", throwable.getMessage());
    }
}
