package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class ValidateRequestCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateRequestCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Validating adoption request for request: {}", request.getId());
        
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request)
            .evaluateEntity(AdoptionRequest.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();
        
        // Validate entity exists and is valid
        if (entity == null) {
            logger.warn("AdoptionRequest entity is null");
            return EvaluationOutcome.fail("AdoptionRequest entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (!entity.isValid()) {
            logger.warn("AdoptionRequest entity is not valid: {}", entity.getId());
            return EvaluationOutcome.fail("AdoptionRequest entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Validate required fields
        EvaluationOutcome basicValidation = validateBasicFields(entity);
        if (basicValidation.isFailure()) {
            return basicValidation;
        }
        
        // Validate business rules
        EvaluationOutcome businessValidation = validateBusinessRules(entity);
        if (businessValidation.isFailure()) {
            return businessValidation;
        }
        
        // Validate request date
        EvaluationOutcome dateValidation = validateRequestDate(entity);
        if (dateValidation.isFailure()) {
            return dateValidation;
        }
        
        logger.info("AdoptionRequest {} is valid and approved", entity.getId());
        return EvaluationOutcome.success();
    }
    
    private EvaluationOutcome validateBasicFields(AdoptionRequest entity) {
        // Check petId
        if (entity.getPetId() == null || entity.getPetId().isBlank()) {
            logger.warn("AdoptionRequest {} has no petId", entity.getId());
            return EvaluationOutcome.fail("Pet ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Check userId
        if (entity.getUserId() == null || entity.getUserId().isBlank()) {
            logger.warn("AdoptionRequest {} has no userId", entity.getId());
            return EvaluationOutcome.fail("User ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Check status
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            logger.warn("AdoptionRequest {} has no status", entity.getId());
            return EvaluationOutcome.fail("Status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        return EvaluationOutcome.success();
    }
    
    private EvaluationOutcome validateBusinessRules(AdoptionRequest entity) {
        // Validate status is in allowed values
        String status = entity.getStatus().toUpperCase();
        if (!isValidStatus(status)) {
            logger.warn("AdoptionRequest {} has invalid status: {}", entity.getId(), entity.getStatus());
            return EvaluationOutcome.fail(
                String.format("Invalid status: %s. Allowed values: PENDING, APPROVED, REJECTED", entity.getStatus()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }
        
        // Additional business rule: Check if the request is in a valid state for processing
        if ("REJECTED".equals(status)) {
            logger.info("AdoptionRequest {} is rejected", entity.getId());
            return EvaluationOutcome.fail("Request has been rejected", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        return EvaluationOutcome.success();
    }
    
    private EvaluationOutcome validateRequestDate(AdoptionRequest entity) {
        if (entity.getRequestDate() == null || entity.getRequestDate().isBlank()) {
            logger.warn("AdoptionRequest {} has no request date", entity.getId());
            return EvaluationOutcome.fail("Request date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        try {
            LocalDateTime requestDate = LocalDateTime.parse(entity.getRequestDate(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            LocalDateTime now = LocalDateTime.now();
            
            // Check if request date is not in the future
            if (requestDate.isAfter(now)) {
                logger.warn("AdoptionRequest {} has future request date: {}", entity.getId(), entity.getRequestDate());
                return EvaluationOutcome.fail("Request date cannot be in the future", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            
            // Check if request is not too old (e.g., more than 30 days)
            if (requestDate.isBefore(now.minusDays(30))) {
                logger.warn("AdoptionRequest {} is too old: {}", entity.getId(), entity.getRequestDate());
                return EvaluationOutcome.fail("Request is too old (more than 30 days)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            
        } catch (DateTimeParseException e) {
            logger.warn("AdoptionRequest {} has invalid request date format: {}", entity.getId(), entity.getRequestDate());
            return EvaluationOutcome.fail("Invalid request date format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        return EvaluationOutcome.success();
    }
    
    private boolean isValidStatus(String status) {
        return "PENDING".equals(status) || "APPROVED".equals(status) || "REJECTED".equals(status);
    }
}
