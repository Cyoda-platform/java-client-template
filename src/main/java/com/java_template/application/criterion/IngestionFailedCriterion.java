package com.java_template.application.criterion;

import com.java_template.application.entity.PetIngestionJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IngestionFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IngestionFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking IngestionFailedCriterion for request: {}", request.getId());
        return serializer.withRequest(request)
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
        PetIngestionJob petIngestionJob = context.entity();

        if (petIngestionJob == null) {
            return EvaluationOutcome.fail("PetIngestionJob entity is null.", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Based on the functional requirements and the processor logic, failure is indicated by the presence of an error message.
        if (petIngestionJob.getErrorMessage() != null && !petIngestionJob.getErrorMessage().isBlank()) {
            logger.info("IngestionFailedCriterion passed for job {}: Error message present - {}", petIngestionJob.getTechnicalId(), petIngestionJob.getErrorMessage());
            return EvaluationOutcome.success();
        } else {
            logger.warn("IngestionFailedCriterion failed for job {}: No error message found.", petIngestionJob.getTechnicalId());
            return EvaluationOutcome.fail("Ingestion process did not report an error.", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}