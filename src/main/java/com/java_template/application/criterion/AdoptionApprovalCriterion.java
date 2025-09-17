package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoption.version_1.Adoption;
import com.java_template.application.entity.owner.version_1.Owner;
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
 * AdoptionApprovalCriterion - Checks if adoption can be approved
 * 
 * Purpose: Verify owner is approved and pet is reserved before approving adoption
 * Used in: under_review -> approved transition
 */
@Component
public class AdoptionApprovalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionApprovalCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking AdoptionApproval criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Adoption.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the adoption entity
     * Checks if owner is approved and pet is reserved
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Adoption> context) {
        Adoption entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("Adoption entity is null");
            return EvaluationOutcome.fail("Adoption entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("Adoption entity is not valid");
            return EvaluationOutcome.fail("Adoption entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Find owner by adoption.ownerId
        EntityWithMetadata<Owner> ownerWithMetadata = findOwnerByOwnerId(entity.getOwnerId());
        
        if (ownerWithMetadata == null) {
            logger.warn("Owner {} not found for adoption {}", entity.getOwnerId(), entity.getAdoptionId());
            return EvaluationOutcome.fail(
                String.format("Owner %s not found for adoption %s", entity.getOwnerId(), entity.getAdoptionId()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        String ownerState = ownerWithMetadata.metadata().getState();
        if (!"approved".equals(ownerState)) {
            logger.warn("Owner {} is in state '{}', not 'approved' for adoption {}", 
                       entity.getOwnerId(), ownerState, entity.getAdoptionId());
            return EvaluationOutcome.fail(
                String.format("Owner %s is in state '%s', not 'approved' for adoption %s", 
                             entity.getOwnerId(), ownerState, entity.getAdoptionId()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Find pet by adoption.petId
        EntityWithMetadata<Pet> petWithMetadata = findPetByPetId(entity.getPetId());
        
        if (petWithMetadata == null) {
            logger.warn("Pet {} not found for adoption {}", entity.getPetId(), entity.getAdoptionId());
            return EvaluationOutcome.fail(
                String.format("Pet %s not found for adoption %s", entity.getPetId(), entity.getAdoptionId()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        String petState = petWithMetadata.metadata().getState();
        if (!"reserved".equals(petState)) {
            logger.warn("Pet {} is in state '{}', not 'reserved' for adoption {}", 
                       entity.getPetId(), petState, entity.getAdoptionId());
            return EvaluationOutcome.fail(
                String.format("Pet %s is in state '%s', not 'reserved' for adoption %s", 
                             entity.getPetId(), petState, entity.getAdoptionId()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.info("Adoption {} can be approved: owner {} is approved, pet {} is reserved", 
                   entity.getAdoptionId(), entity.getOwnerId(), entity.getPetId());
        
        return EvaluationOutcome.success();
    }

    /**
     * Find owner by owner ID
     */
    private EntityWithMetadata<Owner> findOwnerByOwnerId(String ownerId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Owner.ENTITY_NAME)
                    .withVersion(Owner.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.ownerId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(ownerId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Owner>> owners = entityService.search(modelSpec, condition, Owner.class);
            
            if (owners.isEmpty()) {
                return null;
            }
            
            return owners.get(0);
        } catch (Exception e) {
            logger.error("Error finding owner for ownerId {}: {}", ownerId, e.getMessage());
            return null;
        }
    }

    /**
     * Find pet by pet ID
     */
    private EntityWithMetadata<Pet> findPetByPetId(String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Pet.ENTITY_NAME)
                    .withVersion(Pet.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(petId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, condition, Pet.class);
            
            if (pets.isEmpty()) {
                return null;
            }
            
            return pets.get(0);
        } catch (Exception e) {
            logger.error("Error finding pet for petId {}: {}", petId, e.getMessage());
            return null;
        }
    }
}
