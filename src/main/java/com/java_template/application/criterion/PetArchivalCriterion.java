package com.java_template.application.criterion;

import com.java_template.application.entity.pet_entity.version_1.PetEntity;
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

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * PetArchivalCriterion - Check if pet should be archived based on inactivity
 * Transition: archive_pet (analyzed → archived)
 */
@Component
public class PetArchivalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetArchivalCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking PetArchival criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(PetEntity.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Check if pet should be archived based on sales inactivity and low performance
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetEntity> context) {
        PetEntity entity = context.entityWithMetadata().entity();

        // Check if entity is null
        if (entity == null) {
            logger.warn("PetEntity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        LocalDateTime now = LocalDateTime.now();

        // Check if there are no sales recorded
        if (entity.getLastSaleDate() == null) {
            // No sales recorded, check creation date
            if (entity.getCreatedAt() == null) {
                logger.warn("Pet {} has no creation date", entity.getPetId());
                return EvaluationOutcome.fail("Pet creation date is required for archival evaluation", 
                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            Duration timeSinceCreation = Duration.between(entity.getCreatedAt(), now);
            if (timeSinceCreation.toDays() > 90) {
                logger.debug("Pet {} eligible for archival - {} days since creation with no sales", 
                        entity.getPetId(), timeSinceCreation.toDays());
                return EvaluationOutcome.success();
            }

            logger.debug("Pet {} not eligible for archival - only {} days since creation", 
                    entity.getPetId(), timeSinceCreation.toDays());
            return EvaluationOutcome.fail("Pet not old enough for archival without sales", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check time since last sale
        Duration timeSinceLastSale = Duration.between(entity.getLastSaleDate(), now);
        if (timeSinceLastSale.toDays() > 60) {
            logger.debug("Pet {} eligible for archival - {} days since last sale", 
                    entity.getPetId(), timeSinceLastSale.toDays());
            return EvaluationOutcome.success();
        }

        // Check if sales volume is very low and some time has passed
        Integer salesVolume = entity.getSalesVolume() != null ? entity.getSalesVolume() : 0;
        if (salesVolume < 5 && timeSinceLastSale.toDays() > 30) {
            logger.debug("Pet {} eligible for archival - low sales volume ({}) and {} days since last sale", 
                    entity.getPetId(), salesVolume, timeSinceLastSale.toDays());
            return EvaluationOutcome.success();
        }

        logger.debug("Pet {} not eligible for archival - recent activity or good sales performance", entity.getPetId());
        return EvaluationOutcome.fail("Pet has recent activity or good sales performance", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
