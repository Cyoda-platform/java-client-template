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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

@Component
public class ImageQualityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ImageQualityCriterion(SerializerFactory serializerFactory) {
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
        return "ImageQualityCriterion".equals(modelSpec.operationName());
    }

    @SuppressWarnings("unchecked")
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet pet = context.entity();
         if (pet == null) {
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         List<String> photos = null;
         // Use reflection to try several common getter names to avoid compile-time dependency on a specific method name.
         String[] candidateGetters = {"getPhotos", "getPhotoUrls", "getImages", "getImageUrls", "getPictures", "getPictureUrls"};
         for (String getter : candidateGetters) {
             try {
                 Method m = pet.getClass().getMethod(getter);
                 Object res = m.invoke(pet);
                 if (res == null) {
                     continue;
                 }
                 if (res instanceof List) {
                     photos = (List<String>) res;
                     break;
                 }
                 if (res instanceof String[]) {
                     photos = Arrays.asList((String[]) res);
                     break;
                 }
                 if (res instanceof String) {
                     photos = List.of((String) res);
                     break;
                 }
             } catch (NoSuchMethodException ignored) {
                 // try next candidate
             } catch (Exception e) {
                 logger.warn("{}: failed to invoke getter '{}' on Pet: {}", className, getter, e.getMessage());
                 // don't break here; try other getters
             }
         }

         if (photos == null || photos.isEmpty()) {
             return EvaluationOutcome.fail("No photos provided for pet", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         int index = 0;
         for (String url : photos) {
             index++;
             if (url == null || url.isBlank()) {
                 return EvaluationOutcome.fail("Photo at index " + index + " is blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             String lower = url.trim().toLowerCase();
             if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                 return EvaluationOutcome.fail("Photo URL must be absolute (http/https): " + url, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // Check for common image file extensions
             if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif"))) {
                 return EvaluationOutcome.fail("Photo URL does not point to a common image format: " + url, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}