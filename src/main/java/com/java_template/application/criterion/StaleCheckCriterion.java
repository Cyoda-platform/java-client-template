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

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class StaleCheckCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // default threshold 24 hours, configurable via STALE_THRESHOLD_HOURS env var
    private final Duration threshold;

    public StaleCheckCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        String env = System.getenv("STALE_THRESHOLD_HOURS");
        Duration t = Duration.ofHours(24);
        if (env != null) {
            try {
                long h = Long.parseLong(env);
                if (h > 0) t = Duration.ofHours(h);
            } catch (NumberFormatException ex) {
                logger.warn("Invalid STALE_THRESHOLD_HOURS='{}' falling back to default 24h", env);
            }
        }
        this.threshold = t;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
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
        if (book == null) {
            return EvaluationOutcome.fail("Book entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String last = book.getLastIngestedAt();
        if (last == null || last.isBlank()) {
            // Missing lastIngestedAt should be considered stale to trigger reingestion
            return EvaluationOutcome.fail("Missing lastIngestedAt -> considered stale", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        try {
            Instant ingested = Instant.parse(last);
            Instant now = Instant.now();
            Duration age = Duration.between(ingested, now);
            if (age.compareTo(threshold) > 0) {
                return EvaluationOutcome.fail("Book is stale", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            return EvaluationOutcome.success();
        } catch (DateTimeParseException ex) {
            logger.warn("Invalid lastIngestedAt format for book id={}: {}", book.getOpenLibraryId(), last);
            return EvaluationOutcome.fail("Invalid lastIngestedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        } catch (Exception ex) {
            logger.error("Unexpected error during stale check for book id={}: {}", book.getOpenLibraryId(), ex.getMessage(), ex);
            return EvaluationOutcome.fail("Unexpected error", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
