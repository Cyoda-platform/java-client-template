package com.java_template.application.criterion.eod_batch;

import com.java_template.application.criterion.accrual.IsBusinessDayCriterion;
import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Composite criterion that validates all EOD accrual batch start requirements.
 * Combines IsBusinessDay, NoActiveBatchForDate, and UserHasPermission checks.
 */
@Component
public class EODAccrualBatchValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final IsBusinessDayCriterion isBusinessDayCriterion;
    private final NoActiveBatchForDateCriterion noActiveBatchForDateCriterion;
    private final UserHasPermissionCriterion userHasPermissionCriterion;
    private final String className = this.getClass().getSimpleName();

    public EODAccrualBatchValidationCriterion(
            SerializerFactory serializerFactory,
            EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.isBusinessDayCriterion = new IsBusinessDayCriterion(serializerFactory);
        this.noActiveBatchForDateCriterion = new NoActiveBatchForDateCriterion(serializerFactory, entityService);
        this.userHasPermissionCriterion = new UserHasPermissionCriterion(serializerFactory);
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EODAccrualBatchValidation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EODAccrualBatch.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates all EOD batch start requirements.
     * This method is public to allow the negative criterion and error processor to call it.
     */
    public EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> context) {
        EODAccrualBatch batch = context.entityWithMetadata().entity();

        // Check 1: Is business day
        // Note: IsBusinessDayCriterion expects Accrual, but we need to adapt it for EODAccrualBatch
        // For now, we'll create a temporary Accrual-like context
        CriterionSerializer.CriterionEntityEvaluationContext<com.java_template.application.entity.accrual.version_1.Accrual> accrualContext =
            createAccrualContextFromBatch(context);
        EvaluationOutcome businessDayOutcome = isBusinessDayCriterion.validateBusinessDay(accrualContext);

        if (businessDayOutcome != null && businessDayOutcome.isFailure()) {
            logger.debug("Business day validation failed for batch: {}", batch.getBatchId());
            return businessDayOutcome;
        }

        // Check 2: No active batch for date
        EvaluationOutcome noActiveBatchOutcome = noActiveBatchForDateCriterion.validateNoActiveBatch(context);

        if (noActiveBatchOutcome != null && noActiveBatchOutcome.isFailure()) {
            logger.debug("No active batch validation failed for batch: {}", batch.getBatchId());
            return noActiveBatchOutcome;
        }

        // Check 3: User has permission
        EvaluationOutcome userPermissionOutcome = userHasPermissionCriterion.validateUserPermission(context);

        if (userPermissionOutcome != null && userPermissionOutcome.isFailure()) {
            logger.debug("User permission validation failed for batch: {}", batch.getBatchId());
            return userPermissionOutcome;
        }

        logger.debug("All EOD batch validations passed for batch: {}", batch.getBatchId());
        return EvaluationOutcome.success();
    }

    /**
     * Creates an Accrual-like context from the batch context for business day validation.
     * IsBusinessDayCriterion expects an Accrual entity, so we create a minimal Accrual
     * with just the asOfDate field populated.
     */
    private CriterionSerializer.CriterionEntityEvaluationContext<Accrual> createAccrualContextFromBatch(
            CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> batchContext) {

        EODAccrualBatch batch = batchContext.entityWithMetadata().entity();

        // Create a minimal Accrual entity with just the asOfDate
        Accrual tempAccrual = new Accrual();
        tempAccrual.setAsOfDate(batch.getAsOfDate());
        tempAccrual.setAccrualId("temp-for-validation");

        // Create metadata wrapper
        EntityWithMetadata<Accrual> accrualWithMetadata = new EntityWithMetadata<>(
            tempAccrual,
            batchContext.entityWithMetadata().metadata()
        );

        return new CriterionSerializer.CriterionEntityEvaluationContext<>(
            batchContext.request(),
            accrualWithMetadata
        );
    }
}

