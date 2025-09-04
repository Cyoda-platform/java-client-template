package com.java_template.application.criterion;

import com.java_template.application.entity.book.version_1.Book;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BookDataValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BookDataValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Validating Book data for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Book.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Book> context) {
        Book book = context.entity();
        
        // Validate book ID
        if (book.getBookId() == null || book.getBookId() <= 0) {
            return EvaluationOutcome.fail("Book ID is required and must be positive", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate title
        if (book.getTitle() == null || book.getTitle().trim().isEmpty()) {
            return EvaluationOutcome.fail("Book title is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate description
        if (book.getDescription() == null || book.getDescription().length() < 10) {
            return EvaluationOutcome.fail("Book description is required and must be at least 10 characters", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate page count
        if (book.getPageCount() == null || book.getPageCount() <= 0 || book.getPageCount() > 10000) {
            return EvaluationOutcome.fail("Page count must be between 1 and 10000", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate excerpt
        if (book.getExcerpt() == null || book.getExcerpt().length() < 5) {
            return EvaluationOutcome.fail("Book excerpt is required and must be at least 5 characters", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate publish date
        if (book.getPublishDate() == null) {
            return EvaluationOutcome.fail("Publish date is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (book.getPublishDate().isAfter(LocalDateTime.now())) {
            return EvaluationOutcome.fail("Publish date cannot be in the future", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate retrieved timestamp
        if (book.getRetrievedAt() == null) {
            return EvaluationOutcome.fail("Retrieved timestamp is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (book.getRetrievedAt().isAfter(LocalDateTime.now())) {
            return EvaluationOutcome.fail("Retrieved timestamp cannot be in the future", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.info("Book data validation passed for book ID: {}", book.getBookId());
        return EvaluationOutcome.success();
    }
}
