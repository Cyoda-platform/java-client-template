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
public class UniquePetCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UniquePetCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entity();
        if (pet == null) {
            return EvaluationOutcome.fail("Pet not provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // If externalId exists, check for duplicates
        if (pet.getExternalId() != null && !pet.getExternalId().isEmpty()) {
            try {
                Pet found = context.lookupByField(Pet.class, "externalId", pet.getExternalId());
                if (found != null && !found.getId().equals(pet.getId())) {
                    return EvaluationOutcome.fail("Duplicate externalId", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            } catch (Exception e) {
                logger.debug("Field lookup not available in criterion context: {}", e.getMessage());
            }
        }

        // Fallback deduplication by deterministic key: name+species+breed+age
        try {
            Pet found = context.lookupByFields(Pet.class, new String[]{"name","species","breed","age"}, new Object[]{pet.getName(), pet.getSpecies(), pet.getBreed(), pet.getAge()});
            if (found != null && !found.getId().equals(pet.getId())) {
                return EvaluationOutcome.fail("Duplicate by key", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        } catch (Exception e) {
            logger.debug("Multi-field lookup not available in criterion context: {}", e.getMessage());
        }

        return EvaluationOutcome.success();
    }
}
