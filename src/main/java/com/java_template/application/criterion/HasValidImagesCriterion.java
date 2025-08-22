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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@Component
public class HasValidImagesCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HasValidImagesCriterion(SerializerFactory serializerFactory) {
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

         List<String> images = pet.getImages();

         if (images == null || images.isEmpty()) {
             logger.debug("Pet {} has no images", pet != null ? pet.getId() : "unknown");
             return EvaluationOutcome.fail("No images provided for pet", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         for (String img : images) {
             if (img == null || img.isBlank()) {
                 logger.debug("Pet {} contains blank image entry", pet != null ? pet.getId() : "unknown");
                 return EvaluationOutcome.fail("One or more image URLs are blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             try {
                 URL url = new URL(img);
                 String protocol = url.getProtocol();
                 if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                     logger.debug("Pet {} has image with unsupported protocol: {}", pet != null ? pet.getId() : "unknown", img);
                     return EvaluationOutcome.fail("Image URL must use http or https: " + img, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (MalformedURLException e) {
                 logger.debug("Pet {} has malformed image URL: {}", pet != null ? pet.getId() : "unknown", img);
                 return EvaluationOutcome.fail("Invalid image URL: " + img, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}