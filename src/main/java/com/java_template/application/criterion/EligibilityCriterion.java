package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
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
public class EligibilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EligibilityCriterion(SerializerFactory serializerFactory) {
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
        // Eligibility depends on both pet and requesting user; attempt to lookup user id from pet.requestedBy
        String userId = pet.getRequestedBy();
        if (userId == null || userId.isEmpty()) {
            return EvaluationOutcome.fail("No requester present on pet", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        User user = null;
        try {
            user = context.lookup(User.class, userId);
        } catch (Exception e) {
            logger.debug("User lookup not available in criterion context for id {}: {}", userId, e.getMessage());
        }

        if (pet == null) {
            return EvaluationOutcome.fail("Pet not provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (!"under_review".equalsIgnoreCase(pet.getStatus())) {
            return EvaluationOutcome.fail("Pet is not under review", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (user == null) {
            // Cannot fully evaluate without user details; mark as failure
            return EvaluationOutcome.fail("Requester details unavailable", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // user.status must be active and verified
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            return EvaluationOutcome.fail("User is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if ("unverified".equalsIgnoreCase(user.getStatus())) {
            return EvaluationOutcome.fail("User not verified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // basic adoption limit: assume field favorites size represents active adoptions for simplicity
        int activeAdoptions = user.getFavorites() == null ? 0 : user.getFavorites().size();
        if (activeAdoptions >= 3) {
            return EvaluationOutcome.fail("User has reached adoption limit", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // final pet checks
        if (pet.getAdoptedBy() != null && !pet.getAdoptedBy().isEmpty()) {
            return EvaluationOutcome.fail("Pet already adopted", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
