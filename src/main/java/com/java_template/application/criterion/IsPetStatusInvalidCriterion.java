package com.java_template.application.criterion;

import com.java_template.application.entity.Pet;
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
public class IsPetStatusInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsPetStatusInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsPetStatusInvalidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsPetStatusInvalidCriterion".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Pet entity) {
        // Validate pet status is null or blank or invalid
        String status = entity.getStatus();
        if (status == null || status.isBlank()) {
            return EvaluationOutcome.success(); // Consider empty as invalid (criterion passes for invalid)
        }
        if (!("available".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status) || "sold".equalsIgnoreCase(status))) {
            return EvaluationOutcome.success(); // Consider invalid status as invalid (criterion passes for invalid)
        }
        return EvaluationOutcome.fail("Pet status is valid, not invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
