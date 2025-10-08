package com.java_template.application.criterion.accrual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.AccrualState;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.*;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Criterion to prevent duplicate accruals for the same (loanId, asOfDate) combination.
 *
 * An accrual is considered a duplicate if:
 * - Another accrual exists for the same loanId and asOfDate
 * - The existing accrual is not in a terminal state (SUPERSEDED, FAILED, CANCELED)
 * - The current accrual is not explicitly superseding the existing one
 *
 * This ensures idempotency keyed by (loanId, asOfDate, "DAILY_INTEREST").
 *
 * This is a pure function with no side effects.
 */
@Component
public class NotDuplicateAccrualCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotDuplicateAccrualCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking NotDuplicateAccrual criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Accrual.class, this::validateNotDuplicate)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NotDuplicateAccrual".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that no duplicate accrual exists for the same loanId and asOfDate.
     *
     * @param context The criterion evaluation context containing the accrual
     * @return EvaluationOutcome.success() if not a duplicate, otherwise failure with reason
     */
    public EvaluationOutcome validateNotDuplicate(CriterionSerializer.CriterionEntityEvaluationContext<Accrual> context) {
        Accrual accrual = context.entityWithMetadata().entity();
        String currentAccrualId = context.entityWithMetadata().metadata().getId().toString();

        // Check if entity is null (structural validation)
        if (accrual == null) {
            logger.warn("Accrual entity is null");
            return EvaluationOutcome.fail("Accrual entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        String loanId = accrual.getLoanId();
        LocalDate asOfDate = accrual.getAsOfDate();

        // Check required fields
        if (loanId == null || loanId.trim().isEmpty()) {
            logger.warn("LoanId is null or empty for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail("LoanId is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (asOfDate == null) {
            logger.warn("AsOfDate is null for accrual: {}", accrual.getAccrualId());
            return EvaluationOutcome.fail("AsOfDate is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Search for existing accruals with same loanId and asOfDate
        ModelSpec accrualModelSpec = new ModelSpec()
            .withName(Accrual.ENTITY_NAME)
            .withVersion(Accrual.ENTITY_VERSION);

        SimpleCondition loanIdCondition = new SimpleCondition()
            .withJsonPath("$.loanId")
            .withOperation(Operation.EQUALS)
            .withValue(objectMapper.valueToTree(loanId));

        SimpleCondition asOfDateCondition = new SimpleCondition()
            .withJsonPath("$.asOfDate")
            .withOperation(Operation.EQUALS)
            .withValue(objectMapper.valueToTree(asOfDate.toString()));

        GroupCondition searchCondition = new GroupCondition()
            .withOperator(GroupCondition.Operator.AND)
            .withConditions(List.of(loanIdCondition, asOfDateCondition));

        List<EntityWithMetadata<Accrual>> existingAccruals;
        try {
            existingAccruals = entityService.search(accrualModelSpec, searchCondition, Accrual.class);
        } catch (Exception e) {
            logger.error("Error searching for existing accruals for loan {} and date {}: {}",
                loanId, asOfDate, e.getMessage());
            return EvaluationOutcome.fail(
                String.format("Error searching for existing accruals: %s", e.getMessage()),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        // Filter out the current accrual and terminal states
        for (EntityWithMetadata<Accrual> existingAccrualWithMetadata : existingAccruals) {
            String existingAccrualId = existingAccrualWithMetadata.metadata().getId().toString();

            // Skip if this is the same accrual (update scenario)
            if (existingAccrualId.equals(currentAccrualId)) {
                continue;
            }

            Accrual existingAccrual = existingAccrualWithMetadata.entity();
            AccrualState existingState = AccrualState.valueOf(existingAccrualWithMetadata.metadata().getState());

            // Skip terminal states (these are effectively "deleted" for duplicate purposes)
            if (existingState == AccrualState.SUPERSEDED ||
                existingState == AccrualState.FAILED ||
                existingState == AccrualState.CANCELED) {
                logger.debug("Ignoring existing accrual {} in terminal state {} for duplicate check",
                    existingAccrual.getAccrualId(), existingState);
                continue;
            }

            // Check if current accrual is explicitly superseding this one
            if (accrual.getSupersedesAccrualId() != null &&
                accrual.getSupersedesAccrualId().equals(existingAccrual.getAccrualId())) {
                logger.debug("Current accrual {} is superseding existing accrual {}, allowing duplicate",
                    accrual.getAccrualId(), existingAccrual.getAccrualId());
                continue;
            }

            // Found a non-terminal duplicate that is not being superseded
            logger.warn("Duplicate accrual found for loan {} and date {}: existing accrual {} in state {}",
                loanId, asOfDate, existingAccrual.getAccrualId(), existingState);
            return EvaluationOutcome.fail(
                String.format("Duplicate accrual exists for loan %s and date %s (existing accrual: %s, state: %s)",
                    loanId, asOfDate, existingAccrual.getAccrualId(), existingState),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("No duplicate accrual found for loan {} and date {}", loanId, asOfDate);
        return EvaluationOutcome.success();
    }
}

