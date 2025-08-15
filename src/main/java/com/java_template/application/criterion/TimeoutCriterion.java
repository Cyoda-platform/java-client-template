package com.java_template.application.criterion;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

@Component
public class TimeoutCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    // default timeout 300 seconds
    private final long timeoutSeconds = 300L;

    public TimeoutCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating TimeoutCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(IngestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestJob> context) {
        IngestJob job = context.entity();
        if (job == null) {
            return EvaluationOutcome.fail("IngestJob is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (job.getCreatedAt() == null) {
            return EvaluationOutcome.fail("IngestJob createdAt missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            OffsetDateTime created = OffsetDateTime.parse(job.getCreatedAt());
            OffsetDateTime now = OffsetDateTime.now();
            Duration elapsed = Duration.between(created, now);
            if (elapsed.getSeconds() > timeoutSeconds) {
                logger.info("IngestJob {} exceeded timeout: {} seconds", job.getTechnicalId(), elapsed.getSeconds());
                return EvaluationOutcome.fail("Timeout exceeded", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.error("Error parsing createdAt for IngestJob {}: {}", job.getTechnicalId(), e.getMessage(), e);
            return EvaluationOutcome.fail("Invalid createdAt format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
