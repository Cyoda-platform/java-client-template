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
public class SourceUrlInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public SourceUrlInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("SourceUrlInvalidCriterion initialized with SerializerFactory");
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
        return "SourceUrlInvalidCriterion".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Pet entity) {
        // This criterion should fail if the sourceUrl is invalid
        // Pet entity does not have sourceUrl, so fail if mandatory fields missing
        if (entity.getName() == null || entity.getName().isBlank()) {
            return EvaluationOutcome.fail("Pet name is missing or blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            return EvaluationOutcome.fail("Pet category is missing or blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        // If mandatory fields are present, then this criterion does not fail
        return EvaluationOutcome.fail("Unknown source URL invalid condition", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}
