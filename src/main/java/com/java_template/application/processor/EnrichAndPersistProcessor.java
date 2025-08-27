package com.java_template.application.processor;

import com.java_template.application.entity.book.version_1.Book;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Component
public class EnrichAndPersistProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichAndPersistProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichAndPersistProcessor(SerializerFactory serializerFactory) {
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
        return entity != null && entity.isValid();
    }

    private Book processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Book> context) {
        Book entity = context.entity();

        // Business logic:
        // - Compute a popularityScore based on pageCount and recency of publishDate
        // - Set isPopular flag according to a threshold
        // Notes:
        // - Do not invent or persist additional fields on Book beyond available setters.
        // - If publishDate is invalid/unparseable, we fallback to a safe default score = 0 and isPopular = false.
        double finalScore = 0.0;
        double pageScore = 0.0;
        double recencyNormalized = 0.0;

        // Page score: scale pageCount to a 0..100 range. Heuristic: pageCount of 500 => max.
        try {
            Integer pageCount = entity.getPageCount();
            if (pageCount != null && pageCount > 0) {
                pageScore = Math.min(100.0, (pageCount.doubleValue() / 5.0)); // 500 -> 100
            }
        } catch (Exception ex) {
            logger.warn("Failed to compute pageScore for book id {}: {}", entity.getId(), ex.getMessage());
            pageScore = 0.0;
        }

        // Recency score: more recent publications score higher.
        // Compute years since publish and map to 0..50, then normalize to 0..100 by doubling.
        String publishDateStr = entity.getPublishDate();
        if (publishDateStr != null && !publishDateStr.isBlank()) {
            try {
                LocalDate pubDate = LocalDate.parse(publishDateStr, DateTimeFormatter.ISO_DATE);
                long years = ChronoUnit.YEARS.between(pubDate, LocalDate.now());
                double recencyScore = Math.max(0.0, 50.0 - (years * 10.0)); // 0..50
                recencyNormalized = Math.min(100.0, recencyScore * 2.0); // map to 0..100
            } catch (DateTimeParseException dtpe) {
                logger.warn("Unable to parse publishDate '{}' for book id {}: {}", publishDateStr, entity.getId(), dtpe.getMessage());
                recencyNormalized = 0.0;
            } catch (Exception ex) {
                logger.warn("Unexpected error computing recency for book id {}: {}", entity.getId(), ex.getMessage());
                recencyNormalized = 0.0;
            }
        } else {
            // No publish date provided
            recencyNormalized = 0.0;
        }

        // Combine components with weights: pages (70%), recency (30%)
        finalScore = (pageScore * 0.7) + (recencyNormalized * 0.3);
        // Clamp to 0..100
        finalScore = Math.max(0.0, Math.min(100.0, finalScore));

        // If any parsing or unexpected issues lead to NaN, fallback to 0
        if (Double.isNaN(finalScore) || Double.isInfinite(finalScore)) {
            finalScore = 0.0;
        }

        // Determine popularity threshold (business decision): mark as popular if score >= 75
        boolean popular = finalScore >= 75.0;

        // Apply computed values to the entity (this entity will be persisted automatically by Cyoda)
        entity.setPopularityScore(finalScore);
        entity.setIsPopular(popular);

        logger.info("Enriched book id {}: popularityScore={}, isPopular={}", entity.getId(), finalScore, popular);

        return entity;
    }
}