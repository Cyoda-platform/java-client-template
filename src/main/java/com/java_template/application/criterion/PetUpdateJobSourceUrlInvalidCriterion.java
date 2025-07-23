package com.java_template.application.criterion;

import com.java_template.application.entity.PetUpdateJob;
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
public class PetUpdateJobSourceUrlInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetUpdateJobSourceUrlInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetUpdateJobSourceUrlInvalidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetUpdateJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetUpdateJobSourceUrlInvalidCriterion".equals(modelSpec.operationName()) &&
               "petUpdateJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetUpdateJob entity) {
        if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) {
            return EvaluationOutcome.success(); // empty sourceUrl is invalid but here considered not invalid criterion
        }
        try {
            new java.net.URL(entity.getSourceUrl());
        } catch (java.net.MalformedURLException e) {
            return EvaluationOutcome.success(); // valid invalid criterion if url is malformed
        }
        return EvaluationOutcome.fail("Source URL is valid, so this criterion should fail", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
