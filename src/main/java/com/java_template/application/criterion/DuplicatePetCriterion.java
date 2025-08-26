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
        // Use exact criterion name matching (case-sensitive) as required.
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet pet = context.entity();

         if (pet == null) {
             logger.warn("DuplicatePetCriterion invoked with null entity");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If source metadata has a petstoreId we consider the record traceable to source and not a duplicate by default.
         if (pet.getSourceMetadata() != null && pet.getSourceMetadata().getPetstoreId() != null && !pet.getSourceMetadata().getPetstoreId().isBlank()) {
             return EvaluationOutcome.success();
         }

         // If the system-level id is present, we assume this is an existing record and not a new duplicate candidate.
         if (pet.getId() != null && !pet.getId().isBlank()) {
             return EvaluationOutcome.success();
         }

         // To compute a fingerprint we need at least name and breed (species helps but is optional)
         String name = pet.getName();
         String breed = pet.getBreed();

         if (name == null || name.isBlank() || breed == null || breed.isBlank()) {
             // Not enough data to determine duplicate status -> route to manual review / data quality handling
             return EvaluationOutcome.fail("Insufficient identifying data to determine duplicate status (name or breed missing)",
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Heuristic: records lacking a source id and technical id but having identifying fields should be considered potential duplicates
         // and routed for manual review so that an explicit deduplication lookup/merge can occur later in the pipeline.
         String fingerprint = (name.trim().toLowerCase() + "|" + breed.trim().toLowerCase() + "|" +
             (pet.getSpecies() == null ? "" : pet.getSpecies().trim().toLowerCase()));

         logger.debug("DuplicatePetCriterion computed fingerprint='{}' for pet id='{}'", fingerprint, pet.getId());

         // We cannot query the datastore from this criterion implementation; therefore, conservatively mark as potential duplicate
         // when no source identifier or technical id exists so upstream/downstream processors can do the authoritative lookup/merge.
         return EvaluationOutcome.fail("Potential duplicate detected (missing source id/technical id) — requires deduplication lookup",
             StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}