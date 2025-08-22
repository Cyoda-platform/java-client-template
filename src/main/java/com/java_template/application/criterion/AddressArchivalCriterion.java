package com.java_template.application.criterion;

import com.java_template.application.entity.address.version_1.Address;
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
public class AddressArchivalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AddressArchivalCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Address.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Address> context) {
         Address entity = context.entity();

         // Ensure entity is present
         if (entity == null) {
             logger.warn("AddressArchivalCriterion: null entity provided");
             return EvaluationOutcome.fail("Address entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // userId must be present and non-blank
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             logger.warn("Address {} missing userId, cannot evaluate archival", entity.getId());
             return EvaluationOutcome.fail("Address missing userId", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Do not archive if marked as default for the user
         if (Boolean.TRUE.equals(entity.getIsDefault())) {
             logger.debug("Address {} is default for user {}, archival not allowed", entity.getId(), entity.getUserId());
             return EvaluationOutcome.fail("Default address cannot be archived", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Address must be verified to be eligible for archival
         if (!Boolean.TRUE.equals(entity.getVerified())) {
             logger.debug("Address {} for user {} is not verified (verified={}), archival blocked", entity.getId(), entity.getUserId(), entity.getVerified());
             return EvaluationOutcome.fail("Address must be verified before archival", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status should indicate Verified (business model defines Verified/Unverified/Archived)
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             logger.debug("Address {} has missing status, cannot archive", entity.getId());
             return EvaluationOutcome.fail("Address missing status", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (!"Verified".equalsIgnoreCase(status.trim())) {
             logger.debug("Address {} has status '{}' and is not in 'Verified' state required for archival", entity.getId(), status);
             return EvaluationOutcome.fail("Address must be in 'Verified' status to be archived", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Note: Reference checks (users/orders referencing this address) are outside the scope of a pure-entity check here.
         // The processor responsible for archival should perform snapshot searches to ensure the address is unreferenced.
         logger.info("Address {} evaluated eligible for archival checks: verified={}, isDefault={}, status={}", entity.getId(), entity.getVerified(), entity.getIsDefault(), status);
         return EvaluationOutcome.success();
    }
}