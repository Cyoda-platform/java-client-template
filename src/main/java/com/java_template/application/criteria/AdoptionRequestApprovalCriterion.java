package com.java_template.application.criteria;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdoptionRequestApprovalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public AdoptionRequestApprovalCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("AdoptionRequestApprovalCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking AdoptionRequest validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(AdoptionRequest.class, this::validateAdoptionRequest)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler((error, adoptionRequest) -> {
                    logger.debug("AdoptionRequest validation failed for request: {}", request.getId(), error);
                    return ErrorInfo.validationError("AdoptionRequest validation failed: " + error.getMessage());
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestApprovalCriterion".equals(modelSpec.operationName()) &&
                "adoptionRequest".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateAdoptionRequest(AdoptionRequest adoptionRequest) {
        // Add actual validation logic here
        if (adoptionRequest.getRequestorName() == null || adoptionRequest.getRequestorName().isEmpty()) {
            return EvaluationOutcome.fail("Requestor name is required");
        }
        if (adoptionRequest.getPetId() == null || adoptionRequest.getPetId().isEmpty()) {
            return EvaluationOutcome.fail("Pet ID is required");
        }
        return EvaluationOutcome.success();
    }
}
