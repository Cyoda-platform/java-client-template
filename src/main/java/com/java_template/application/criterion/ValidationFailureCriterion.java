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

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Book.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Book> context) {
        Book entity = context.entity();

        if (entity == null) {
            logger.warn("ValidationFailureCriterion: received null entity in context");
            return EvaluationOutcome.fail("Book entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // id is mandatory
        if (entity.getId() == null) {
            return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // title is mandatory and must not be blank
        String title = entity.getTitle();
        if (title == null || title.isBlank()) {
            return EvaluationOutcome.fail("title is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // pageCount is mandatory and must be non-negative
        Integer pageCount = entity.getPageCount();
        if (pageCount == null) {
            return EvaluationOutcome.fail("pageCount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pageCount < 0) {
            return EvaluationOutcome.fail("pageCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // publishDate is mandatory and must be a valid ISO date (yyyy-MM-dd)
        String publishDate = entity.getPublishDate();
        if (publishDate == null || publishDate.isBlank()) {
            return EvaluationOutcome.fail("publishDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } else {
            try {
                LocalDate.parse(publishDate);
            } catch (DateTimeParseException ex) {
                return EvaluationOutcome.fail("publishDate is not a valid ISO date", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // fetchTimestamp is mandatory and should be a valid ISO instant timestamp
        String fetchTimestamp = entity.getFetchTimestamp();
        if (fetchTimestamp == null || fetchTimestamp.isBlank()) {
            return EvaluationOutcome.fail("fetchTimestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } else {
            try {
                Instant.parse(fetchTimestamp);
            } catch (DateTimeParseException ex) {
                return EvaluationOutcome.fail("fetchTimestamp is not a valid ISO timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // popularityScore if present must be a valid non-negative number
        Double popularityScore = entity.getPopularityScore();
        if (popularityScore != null) {
            if (Double.isNaN(popularityScore) || popularityScore < 0.0) {
                return EvaluationOutcome.fail("popularityScore is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // description and excerpt are optional; no further checks required here
        return EvaluationOutcome.success();
    }
}