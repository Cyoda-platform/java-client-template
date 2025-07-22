package com.java_template.application.criterion;

import com.java_template.application.entity.PetJob;
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
public class IsAddPetJobTypeCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsAddPetJobTypeCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsAddPetJobTypeCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(PetJob.class, this::validateEntity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsAddPetJobTypeCriterion".equals(modelSpec.operationName()) &&
                "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetJob entity) {
        if (entity.getJobType() == null || entity.getJobType().isBlank()) {
            return EvaluationOutcome.fail("Job type is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getJobType().equalsIgnoreCase("AddPet")) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Job type is not AddPet", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
