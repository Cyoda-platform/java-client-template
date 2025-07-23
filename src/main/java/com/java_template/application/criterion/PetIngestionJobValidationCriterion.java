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
public class PetIngestionJobValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetIngestionJobValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetIngestionJobValidationCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobValidationCriterion".equals(modelSpec.operationName()) &&
               "petIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetIngestionJob entity) {
        // Validation based on business requirements from functional docs
        // Check required fields: jobId, source, status

        if (entity.getJobId() == null || entity.getJobId().isBlank()) {
            return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getSource() == null || entity.getSource().isBlank()) {
            return EvaluationOutcome.fail("source is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Additional validation: status must be one of PENDING, PROCESSING, COMPLETED, FAILED
        String status = entity.getStatus();
        if (!("PENDING".equals(status) || "PROCESSING".equals(status) || "COMPLETED".equals(status) || "FAILED".equals(status))) {
            return EvaluationOutcome.fail("status must be one of PENDING, PROCESSING, COMPLETED, FAILED", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
