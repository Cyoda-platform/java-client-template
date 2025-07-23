package com.java_template.application.criterion;

import com.java_template.application.entity.PetAdoptionJob;
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
public class PetNotAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetNotAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetNotAvailableCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetAdoptionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetNotAvailableCriterion".equals(modelSpec.operationName()) &&
               "petAdoptionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetAdoptionJob job) {
        if (job == null) {
            return EvaluationOutcome.fail("PetAdoptionJob entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (job.getPetId() == null || job.getPetId().isBlank()) {
            return EvaluationOutcome.fail("Pet ID is missing in adoption job", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (job.getStatus() == null) {
            return EvaluationOutcome.fail("Job status is not set", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Criterion logic: Pet must not be available if job status is PROCESSING (example business rule)
        if (job.getStatus().name().equalsIgnoreCase("PROCESSING")) {
            return EvaluationOutcome.fail("Pet is currently not available due to ongoing processing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
