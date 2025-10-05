package com.java_template.application.criterion;

import com.java_template.application.entity.party.version_1.Party;
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

/**
 * ABOUTME: This criterion validates new Party entities during creation,
 * ensuring all required fields are present and business rules are met.
 */
@Component
public class NewPartyValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NewPartyValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking new Party validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Party.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Party> context) {
        Party party = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (party == null) {
            logger.warn("Party entity is null");
            return EvaluationOutcome.fail("Party entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!party.isValid()) {
            logger.warn("Party entity is not valid: {}", party.getPartyId());
            return EvaluationOutcome.fail("Party entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate required fields
        if (party.getPartyId() == null || party.getPartyId().trim().isEmpty()) {
            logger.warn("Party ID is required");
            return EvaluationOutcome.fail("Party ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (party.getLegalName() == null || party.getLegalName().trim().isEmpty()) {
            logger.warn("Legal name is required for party: {}", party.getPartyId());
            return EvaluationOutcome.fail("Legal name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (party.getJurisdiction() == null || party.getJurisdiction().trim().isEmpty()) {
            logger.warn("Jurisdiction is required for party: {}", party.getPartyId());
            return EvaluationOutcome.fail("Jurisdiction is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate LEI format if provided (20 character alphanumeric)
        if (party.getLei() != null && !party.getLei().trim().isEmpty()) {
            String lei = party.getLei().trim();
            if (lei.length() != 20 || !lei.matches("^[A-Z0-9]{20}$")) {
                logger.warn("Invalid LEI format for party {}: {}", party.getPartyId(), lei);
                return EvaluationOutcome.fail("LEI must be 20 alphanumeric characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Validate jurisdiction format (ISO country code)
        String jurisdiction = party.getJurisdiction().trim().toUpperCase();
        if (jurisdiction.length() != 2 || !jurisdiction.matches("^[A-Z]{2}$")) {
            logger.warn("Invalid jurisdiction format for party {}: {}", party.getPartyId(), jurisdiction);
            return EvaluationOutcome.fail("Jurisdiction must be a 2-letter ISO country code", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}

