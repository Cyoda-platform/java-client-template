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

import java.util.ArrayList;
import java.util.List;

@Component
public class MissingOrInvalidImagesCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MissingOrInvalidImagesCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet pet = context.entity();

         // If pet is null (defensive), treat as validation failure
         if (pet == null) {
             logger.warn("Pet entity is null in MissingOrInvalidImagesCriterion");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         List<String> images = pet.getImages();

         // Missing images
         if (images == null || images.isEmpty()) {
             return EvaluationOutcome.fail("No images provided for pet", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate each image URL: must be non-null, non-blank and start with http:// or https://
         List<String> invalidUrls = new ArrayList<>();
         for (String img : images) {
             if (img == null || img.isBlank()) {
                 invalidUrls.add(String.valueOf(img));
                 continue;
             }
             String lower = img.trim().toLowerCase();
             if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                 invalidUrls.add(img);
             }
         }

         if (!invalidUrls.isEmpty()) {
             String msg = "Invalid image URLs: " + invalidUrls;
             logger.debug("Pet {} has invalid images: {}", pet.getId(), invalidUrls);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}