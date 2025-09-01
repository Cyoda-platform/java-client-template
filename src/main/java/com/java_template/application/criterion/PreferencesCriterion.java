package com.java_template.application.criterion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.owner.version_1.Owner;
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

@Component
public class PreferencesCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper mapper = new ObjectMapper();

    public PreferencesCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Owner.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
         Owner entity = context.entity();

         // preferences must be provided
         String prefs = entity.getPreferences();
         if (prefs == null || prefs.isBlank()) {
             return EvaluationOutcome.fail("Owner preferences are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Try to parse as JSON. If not JSON, accept free-text but warn (logged).
         try {
             JsonNode root = mapper.readTree(prefs);
             if (!root.isObject()) {
                 // it's JSON but not an object (e.g., array or primitive) -> data quality issue
                 logger.warn("Owner {} preferences parsed as JSON but not an object: {}", entity.getId(), prefs);
                 return EvaluationOutcome.fail("Preferences must be a JSON object or a readable textual preference", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             boolean hasSpecies = root.has("species") && !root.get("species").asText("").isBlank();
             boolean hasBreed = root.has("breed") && !root.get("breed").asText("").isBlank();
             boolean hasAgeMin = root.has("ageMin") && root.get("ageMin").canConvertToInt();
             boolean hasAgeMax = root.has("ageMax") && root.get("ageMax").canConvertToInt();

             if (!hasSpecies && !hasBreed && !hasAgeMin && !hasAgeMax) {
                 return EvaluationOutcome.fail("Preferences JSON must contain at least one of: species, breed, ageMin, ageMax", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             if (hasSpecies && root.get("species").asText("").isBlank()) {
                 return EvaluationOutcome.fail("preferences.species must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (hasBreed && root.get("breed").asText("").isBlank()) {
                 return EvaluationOutcome.fail("preferences.breed must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             if (hasAgeMin && hasAgeMax) {
                 int ageMin = root.get("ageMin").asInt();
                 int ageMax = root.get("ageMax").asInt();
                 if (ageMin < 0 || ageMax < 0) {
                     return EvaluationOutcome.fail("ageMin/ageMax must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
                 if (ageMin > ageMax) {
                     return EvaluationOutcome.fail("preferences.ageMin cannot be greater than preferences.ageMax", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             } else {
                 // If only one bound provided, ensure it's non-negative
                 if (hasAgeMin && root.get("ageMin").asInt() < 0) {
                     return EvaluationOutcome.fail("preferences.ageMin must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
                 if (hasAgeMax && root.get("ageMax").asInt() < 0) {
                     return EvaluationOutcome.fail("preferences.ageMax must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             }

             // All checks passed for JSON preferences
             return EvaluationOutcome.success();

         } catch (JsonProcessingException e) {
             // Not valid JSON; accept free-text preferences but log a warning so data can be cleaned later
             logger.warn("Owner {} preferences are not valid JSON; treating as free-text: {}", entity.getId(), prefs);
             return EvaluationOutcome.success();
         } catch (Exception e) {
             logger.error("Unexpected error validating preferences for owner {}: {}", entity.getId(), e.getMessage(), e);
             return EvaluationOutcome.fail("Unexpected error validating preferences", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
    }
}