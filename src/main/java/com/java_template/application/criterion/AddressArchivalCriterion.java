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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Address.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Address> context) {
         Address entity = context.entity();
         // Data quality: address must reference a user
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("Address missing userId", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: default addresses must not be archived
         if (Boolean.TRUE.equals(entity.getIsDefault())) {
             return EvaluationOutcome.fail("Default address cannot be archived", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validation: only verified addresses are eligible for archival
         if (!Boolean.TRUE.equals(entity.getVerified())) {
             return EvaluationOutcome.fail("Only verified addresses can be archived", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Optional: status should not be explicitly unverified
         String status = entity.getStatus();
         if (status != null && status.equalsIgnoreCase("Unverified")) {
             return EvaluationOutcome.fail("Address status is Unverified and cannot be archived", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}