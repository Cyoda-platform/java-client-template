package com.java_template.application.criterion.eod_batch;

import com.java_template.application.entity.accrual.version_1.BatchMode;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.common.serializer.*;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Criterion to verify that the user has the required permission for the batch operation.
 *
 * <p>For BACKDATED batch runs, this criterion checks that the initiating user
 * has the "backdated_eod_execute" permission. For TODAY runs, no special
 * permission is required beyond basic EOD execution rights.</p>
 *
 * <p>The required permission can be configured via the criterion config.</p>
 *
 * <p>This is a pure function with no side effects.</p>
 */
@Component
public class UserHasPermissionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    // Default permission required for backdated runs
    private static final String DEFAULT_BACKDATED_PERMISSION = "backdated_eod_execute";

    public UserHasPermissionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking UserHasPermission criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EODAccrualBatch.class, this::validateUserPermission)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "UserHasPermission".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the user has the required permission for the batch operation.
     *
     * @param context The criterion evaluation context containing the batch
     * @return EvaluationOutcome.success() if user has permission, otherwise failure with reason
     */
    EvaluationOutcome validateUserPermission(CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> context) {
        EODAccrualBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("EODAccrualBatch entity is null");
            return EvaluationOutcome.fail("Batch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        String userId = batch.getInitiatedBy();
        BatchMode mode = batch.getMode();

        // Check if userId is null
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("InitiatedBy (userId) is null or empty for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("User ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if mode is null
        if (mode == null) {
            logger.warn("Mode is null for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("Batch mode is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // For TODAY mode, no special permission is required
        if (mode == BatchMode.TODAY) {
            logger.debug("TODAY mode batch - no special permission required for user: {}", userId);
            return EvaluationOutcome.success();
        }

        // For BACKDATED mode, check for backdated permission
        if (mode == BatchMode.BACKDATED) {
            // Use default permission for backdated runs
            String requiredPermission = DEFAULT_BACKDATED_PERMISSION;

            // TODO: In production, integrate with actual permission/authorization service
            // For now, we'll perform a basic check
            boolean hasPermission = checkUserPermission(userId, requiredPermission);

            if (!hasPermission) {
                logger.warn("User {} does not have required permission '{}' for BACKDATED batch: {}",
                    userId, requiredPermission, batch.getBatchId());
                return EvaluationOutcome.fail(
                    String.format("User %s does not have permission '%s' required for backdated EOD runs",
                        userId, requiredPermission),
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }

            logger.debug("User {} has required permission '{}' for BACKDATED batch: {}",
                userId, requiredPermission, batch.getBatchId());
            return EvaluationOutcome.success();
        }

        // Unknown mode
        logger.warn("Unknown batch mode: {} for batch: {}", mode, batch.getBatchId());
        return EvaluationOutcome.fail(
            "Unknown batch mode: " + mode,
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }

    /**
     * Check if the user has the required permission.
     *
     * TODO: In production, this should integrate with the actual authorization service
     * (e.g., Spring Security, OAuth2, or custom permission service).
     *
     * For now, this is a stub implementation that always returns true.
     *
     * @param userId The user ID to check
     * @param permission The required permission
     * @return true if user has permission, false otherwise
     */
    private boolean checkUserPermission(String userId, String permission) {
        // TODO: Replace with actual permission check
        // Example integration points:
        // - Spring Security: SecurityContextHolder.getContext().getAuthentication()
        // - Custom service: permissionService.hasPermission(userId, permission)
        // - OAuth2: Check scopes/authorities in JWT token

        logger.debug("TODO: Implement actual permission check for user {} and permission {}", userId, permission);

        // For now, return true to allow development/testing
        // In production, this should be replaced with actual permission logic
        return true;
    }
}

