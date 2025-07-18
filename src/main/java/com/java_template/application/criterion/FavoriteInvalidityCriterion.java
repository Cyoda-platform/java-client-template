package com.java_template.application.criterion;

import com.java_template.application.entity.Favorite;
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
public class FavoriteInvalidityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public FavoriteInvalidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("FavoriteInvalidityCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Favorite.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FavoriteInvalidityCriterion".equals(modelSpec.operationName()) &&
               "favorite".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Favorite entity) {
        // This criterion might check for conditions that make a Favorite invalid. For example,
        // if the favorite is marked REMOVED but references a non-existent user or pet.
        // However, since we only have Favorite entity properties, we can check for null/blank fields
        // or inconsistency.

        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Status must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getStatus().equals("REMOVED")) {
            // If status is REMOVED, userId or petId should not be null or blank
            if (entity.getUserId() == null || entity.getUserId().isBlank()) {
                return EvaluationOutcome.fail("UserId must be provided for removed favorite", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            if (entity.getPetId() == null || entity.getPetId().isBlank()) {
                return EvaluationOutcome.fail("PetId must be provided for removed favorite", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
