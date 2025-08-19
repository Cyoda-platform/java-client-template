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

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
public class ComputePopularityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ComputePopularityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ComputePopularityProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Book popularity for request: {}", request.getId());

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
        try {
            double pageNorm = 0.0;
            if (book.getPageCount() != null && book.getPageCount() > 0) {
                // simple normalization: use log scale capped to 1.0 for common page ranges
                double val = Math.log(book.getPageCount() + 1);
                double max = Math.log(2000 + 1); // assume 2000 pages as practical max
                pageNorm = Math.min(1.0, val / max);
            }

            double recency = 0.0;
            if (book.getPublishDate() != null) {
                OffsetDateTime now = OffsetDateTime.now();
                long days = Duration.between(book.getPublishDate(), now).toDays();
                // recency score: newer -> closer to 1.0, using exponential decay over 5 years (~1825 days)
                double decayWindow = 1825.0;
                recency = Math.exp(-Math.max(0, days) / decayWindow);
                if (recency > 1.0) recency = 1.0;
            }

            double weightPages = 0.7;
            double weightRecency = 0.3;
            double score = pageNorm * weightPages + recency * weightRecency;
            if (score < 0) score = 0;
            if (score > 1) score = 1;
            book.setPopularityScore(score);
            logger.info("ComputePopularityProcessor: computed score={} for book id={}", score, book.getId());
        } catch (Exception ex) {
            logger.error("ComputePopularityProcessor: error computing popularity for book id=" + book.getId(), ex);
        }
        return book;
    }
}
