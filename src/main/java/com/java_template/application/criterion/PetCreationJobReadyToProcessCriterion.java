package com.java_template.application.criterion;

import com.java_template.application.entity.PetCreationJob;
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
public class PetCreationJobReadyToProcessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetCreationJobReadyToProcessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetCreationJobReadyToProcessCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetCreationJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetCreationJobReadyToProcessCriterion".equals(modelSpec.operationName()) &&
               "petCreationJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetCreationJob entity) {
        // Business Logic:
        // The job is ready to process if the status is PENDING and all required fields are valid.
        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getStatus() == null || !entity.getStatus().equals("PENDING")) {
            return EvaluationOutcome.fail("Job status must be PENDING", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getId() == null || entity.getId().isBlank()) {
            return EvaluationOutcome.fail("ID must not be null or blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getJobId() == null || entity.getJobId().isBlank()) {
            return EvaluationOutcome.fail("Job ID must not be null or blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getPetData() == null || entity.getPetData().isBlank()) {
            return EvaluationOutcome.fail("Pet data must not be null or blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
