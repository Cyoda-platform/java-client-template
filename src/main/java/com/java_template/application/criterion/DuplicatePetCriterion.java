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

@Component
public class DuplicatePetCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DuplicatePetCriterion(SerializerFactory serializerFactory) {
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

         // Basic defensive checks using only entity getters
         if (pet == null) {
             logger.warn("DuplicatePetCriterion invoked with null entity");
             return EvaluationOutcome.success();
         }

         // Ensure minimal required fields for dedupe logic
         if (pet.getName() == null || pet.getName().isBlank()) {
             return EvaluationOutcome.fail("Pet name is required for duplicate detection", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("Pet species is required for duplicate detection", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (pet.getStatus() == null || pet.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Pet status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Duplicate detection strategy:
         // - If a source id (external id) is provided we treat the record as source-authoritative and skip marking it as duplicate here.
         // - If no source id is present we consider name+breed+location a high-risk duplicate signature and surface a data-quality warning/failure.
         String sourceId = pet.getId();
         if (sourceId != null && !sourceId.isBlank()) {
             // We have an external id; assume upstream source id is authoritative for uniqueness.
             return EvaluationOutcome.success();
         }

         // Normalize fields used for fuzzy matching
         String name = pet.getName() == null ? "" : pet.getName().trim().toLowerCase();
         String breed = pet.getBreed() == null ? "" : pet.getBreed().trim().toLowerCase();
         String location = pet.getLocation() == null ? "" : pet.getLocation().trim().toLowerCase();

         // If we don't have enough attributes to perform a fuzzy match, treat as validation failure for dedupe readiness
         if (name.isBlank() || breed.isBlank() || location.isBlank()) {
             // Not enough information to reliably dedupe; surface as data quality issue so it can be reviewed/enriched.
             return EvaluationOutcome.fail(
                 "Insufficient attributes for duplicate detection (requires name, breed and location when source id is missing)",
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE
             );
         }

         // At this point there is no external id and we have a full signature (name+breed+location).
         // Without access to persistent store in this criterion we flag the entity as a potential duplicate for downstream processors
         // which can perform a lookup. This preserves deterministic behavior and surfaces likely duplicates.
         String fingerprint = String.join("|", name, breed, location);
         logger.debug("Potential duplicate fingerprint for pet (no source id): {}", fingerprint);

         return EvaluationOutcome.fail(
             "Potential duplicate detected (missing source id) based on name+breed+location",
             StandardEvalReasonCategories.DATA_QUALITY_FAILURE
         );
    }
}