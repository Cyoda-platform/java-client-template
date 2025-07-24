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
public class PetIngestionJobSourceValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetIngestionJobSourceValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetIngestionJobSourceValidCriterion initialized with SerializerFactory");
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
        return "PetIngestionJobSourceValidCriterion".equals(modelSpec.operationName()) &&
               "petIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetIngestionJob entity) {
        // Business logic: Validate source field is not null, not blank and follows a valid URL pattern
        if (entity.getSource() == null || entity.getSource().isBlank()) {
            return EvaluationOutcome.fail("Source must not be empty", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Simple URL pattern check (can be improved with regex or URL class if needed)
        if (!entity.getSource().startsWith("http://") && !entity.getSource().startsWith("https://")) {
            return EvaluationOutcome.fail("Source must be a valid URL starting with http:// or https://", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
