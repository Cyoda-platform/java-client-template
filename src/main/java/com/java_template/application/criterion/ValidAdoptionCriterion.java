package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoption.version_1.Adoption;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ValidAdoptionCriterion - Verifies adoption can be completed
 * 
 * Purpose: Check if pet adoption can be completed by verifying adoption exists and is approved
 * Used in: reserved -> adopted transition
 */
@Component
public class ValidAdoptionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidAdoptionCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ValidAdoption criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the pet entity
     * Checks if adoption exists and has approval date set
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("Pet entity is not valid");
            return EvaluationOutcome.fail("Pet entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Find adoption by pet.petId
        EntityWithMetadata<Adoption> adoptionWithMetadata = findAdoptionByPetId(entity.getPetId());
        
        if (adoptionWithMetadata == null) {
            logger.warn("No adoption found for pet {}", entity.getPetId());
            return EvaluationOutcome.fail(
                String.format("No adoption found for pet %s", entity.getPetId()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Adoption adoption = adoptionWithMetadata.entity();
        
        // Check if adoption.approvalDate is not null
        if (adoption.getApprovalDate() == null) {
            logger.warn("Adoption {} for pet {} has not been approved yet", 
                       adoption.getAdoptionId(), entity.getPetId());
            return EvaluationOutcome.fail(
                String.format("Adoption %s for pet %s has not been approved yet", 
                             adoption.getAdoptionId(), entity.getPetId()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.info("Valid adoption found for pet {}: adoption {} approved on {}", 
                   entity.getPetId(), adoption.getAdoptionId(), adoption.getApprovalDate());
        
        return EvaluationOutcome.success();
    }

    /**
     * Find adoption by pet ID
     */
    private EntityWithMetadata<Adoption> findAdoptionByPetId(String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Adoption.ENTITY_NAME)
                    .withVersion(Adoption.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(petId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Adoption>> adoptions = entityService.search(modelSpec, condition, Adoption.class);
            
            if (adoptions.isEmpty()) {
                return null;
            }
            
            // Return the first (most recent) adoption
            return adoptions.get(0);
        } catch (Exception e) {
            logger.error("Error finding adoption for pet {}: {}", petId, e.getMessage());
            return null;
        }
    }
}
