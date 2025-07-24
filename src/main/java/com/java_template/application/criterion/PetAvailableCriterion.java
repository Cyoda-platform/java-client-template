package com.java_template.application.criterion;

import com.java_template.application.entity.AdoptionRequest;
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
public class PetAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetAvailableCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(AdoptionRequest.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetAvailableCriterion".equals(modelSpec.operationName()) &&
               "adoptionRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(AdoptionRequest entity) {
        // Business logic: Validate that the pet is available for adoption
        // Since AdoptionRequest does not have pet status, assume external check or status in request
        // Here, check if status is SUBMITTED and petId exists as a proxy
        if (entity.getPetId() == null || entity.getPetId().isBlank()) {
            return EvaluationOutcome.fail("Pet does not exist for availability check", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!"SUBMITTED".equalsIgnoreCase(entity.getStatus())) {
            return EvaluationOutcome.fail("Pet is not available - request status is not SUBMITTED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
