package com.java_template.application.processor;

import com.java_template.application.entity.book.version_1.Book;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class BookAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BookAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Popular words for title analysis
    private static final List<String> POPULAR_WORDS = Arrays.asList(
        "Guide", "Complete", "Ultimate", "Essential", "Mastering", "Advanced", "Beginner", "Professional"
    );

    public BookAnalysisProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Book analysis for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Book.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        Book book = context.entity();

        try {
            // Calculate popularity score based on multiple factors
            double pageScore = calculatePageScore(book.getPageCount()) * 0.3;
            double titleScore = calculateTitlePopularity(book.getTitle()) * 0.2;
            double descriptionScore = calculateDescriptionRichness(book.getDescription()) * 0.2;
            double excerptScore = calculateExcerptQuality(book.getExcerpt()) * 0.1;
            double dateScore = calculateRecencyScore(book.getPublishDate()) * 0.2;

            double totalScore = pageScore + titleScore + descriptionScore + excerptScore + dateScore;
            book.setAnalysisScore(Math.round(totalScore * 1000.0) / 1000.0); // Round to 3 decimal places

            logger.info("Analysis completed for book: {} (Score: {})", book.getTitle(), book.getAnalysisScore());
        } catch (Exception e) {
            logger.error("Failed to analyze book {}: {}", book.getBookId(), e.getMessage(), e);
            throw new RuntimeException("Book analysis failed: " + e.getMessage(), e);
        }

        return book;
    }

    private double calculatePageScore(Integer pageCount) {
        if (pageCount == null || pageCount <= 0) {
            return 0.0;
        }
        // Normalize page count between 0 and 1000, cap at 1.0
        return Math.min(pageCount / 1000.0, 1.0);
    }

    private double calculateTitlePopularity(String title) {
        if (title == null || title.trim().isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        String upperTitle = title.toUpperCase();
        
        for (String word : POPULAR_WORDS) {
            if (upperTitle.contains(word.toUpperCase())) {
                score += 0.2;
            }
        }
        
        return Math.min(score, 1.0);
    }

    private double calculateDescriptionRichness(String description) {
        if (description == null || description.trim().isEmpty()) {
            return 0.1;
        }
        
        int length = description.length();
        if (length < 50) {
            return 0.1;
        }
        if (length > 500) {
            return 1.0;
        }
        
        return length / 500.0;
    }

    private double calculateExcerptQuality(String excerpt) {
        if (excerpt == null || excerpt.trim().isEmpty()) {
            return 0.1;
        }
        
        int length = excerpt.length();
        if (length < 20) {
            return 0.1;
        }
        
        return Math.min(length / 200.0, 1.0);
    }

    private double calculateRecencyScore(LocalDateTime publishDate) {
        if (publishDate == null) {
            return 0.1;
        }
        
        int currentYear = LocalDateTime.now().getYear();
        int publishYear = publishDate.getYear();
        int yearsOld = currentYear - publishYear;
        
        if (yearsOld <= 1) {
            return 1.0;
        }
        if (yearsOld >= 10) {
            return 0.1;
        }
        
        return 1.0 - (yearsOld / 10.0);
    }
}
