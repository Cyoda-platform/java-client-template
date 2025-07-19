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
public class isJobValid implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public isJobValid(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("isJobValid initialized with SerializerFactory");
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
        return "isJobValid".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Pet entity) {
        // Validation for a job validity in the context of Pet entity
        // As per functional requirements, validate mandatory fields for job processing
        if (entity.getPetId() == null || entity.getPetId().isBlank()) {
            return EvaluationOutcome.fail("Pet ID is required for job validity", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            return EvaluationOutcome.fail("Pet name is required for job validity", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            return EvaluationOutcome.fail("Pet category is required for job validity", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Pet status is required for job validity", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Additional business logic can be added here for job validation
        return EvaluationOutcome.success();
    }
}
