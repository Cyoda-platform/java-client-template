package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoValidImagesCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NoValidImagesCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet pet = context.entity();
         if (pet == null) {
             logger.warn("No Pet entity present in evaluation context");
             return EvaluationOutcome.fail("Pet entity missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         List<String> images = pet.getImages();
         if (images == null || images.isEmpty()) {
             logger.debug("Pet {} has no images", pet.getId());
             return EvaluationOutcome.fail("No images provided for pet", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         int validCount = 0;
         for (String img : images) {
             if (img == null) continue;
             String trimmed = img.trim();
             if (trimmed.isEmpty()) continue;
             // Basic URL sanity check: must look like an http/https URL
             if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                 validCount++;
             }
         }

         if (validCount == 0) {
             logger.debug("Pet {} has images but none are valid URLs", pet.getId());
             return EvaluationOutcome.fail("No valid image URLs found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}