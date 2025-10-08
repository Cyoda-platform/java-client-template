package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.entity.party.version_1.Party;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ABOUTME: This criterion validates new Loan entities during creation,
 * ensuring all required fields are present, business rules are met, and referenced parties exist.
 */
@Component
public class NewLoanValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NewLoanValidationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking new Loan validation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Loan.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Loan> context) {
        Loan loan = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (loan == null) {
            logger.warn("Loan entity is null");
            return EvaluationOutcome.fail("Loan entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!loan.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Loan entity is not valid: {}", loan.getLoanId());
            return EvaluationOutcome.fail("Loan entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate term is one of allowed values
        if (loan.getTermMonths() == null ||
            (loan.getTermMonths() != 12 && loan.getTermMonths() != 24 && loan.getTermMonths() != 36)) {
            logger.warn("Invalid term for loan {}: {}", loan.getLoanId(), loan.getTermMonths());
            return EvaluationOutcome.fail("Term must be 12, 24, or 36 months", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate APR is within reasonable range (1% to 25%)
        if (loan.getApr() == null ||
            loan.getApr().compareTo(new BigDecimal("0.01")) < 0 ||
            loan.getApr().compareTo(new BigDecimal("0.25")) > 0) {
            logger.warn("Invalid APR for loan {}: {}", loan.getLoanId(), loan.getApr());
            return EvaluationOutcome.fail("APR must be between 1% and 25%", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate principal amount is positive
        if (loan.getPrincipalAmount() == null || loan.getPrincipalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Invalid principal amount for loan {}: {}", loan.getLoanId(), loan.getPrincipalAmount());
            return EvaluationOutcome.fail("Principal amount must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate that the referenced party exists and is active
        EvaluationOutcome partyValidation = validatePartyExists(loan.getPartyId());
        if (partyValidation.isFailure()) {
            return partyValidation;
        }

        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validatePartyExists(String partyId) {
        if (partyId == null || partyId.trim().isEmpty()) {
            logger.warn("Party ID is required");
            return EvaluationOutcome.fail("Party ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);

        SimpleCondition condition = new SimpleCondition()
                .withJsonPath("$.partyId")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(partyId));

        GroupCondition groupCondition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(condition));

        List<EntityWithMetadata<Party>> parties = entityService.search(modelSpec, groupCondition, Party.class);

        if (parties.isEmpty()) {
            logger.warn("Referenced party does not exist: {}", partyId);
            return EvaluationOutcome.fail("Referenced party does not exist: " + partyId, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        EntityWithMetadata<Party> partyWithMetadata = parties.getFirst();
        String partyState = partyWithMetadata.metadata().getState();
        if (!"active".equals(partyState)) {
            logger.warn("Referenced party is not active: {} (state: {})", partyId, partyState);
            return EvaluationOutcome.fail("Referenced party is not active: " + partyId + " (state: " + partyState + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Party validation successful for: {}", partyId);
        return EvaluationOutcome.success();
    }
}

