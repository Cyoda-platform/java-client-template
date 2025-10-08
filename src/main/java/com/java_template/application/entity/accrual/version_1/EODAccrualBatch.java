package com.java_template.application.entity.accrual.version_1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.util.UUID;

/**
 * EODAccrualBatch orchestrates daily accrual runs for commercial loans.
 *
 * <p>This entity manages the end-of-day (EOD) accrual process, coordinating the creation
 * of individual Accrual entities for eligible loans on a specific business date (asOfDate).
 * It supports both normal daily runs (TODAY mode) and back-dated correction runs (BACKDATED mode).</p>
 *
 * <p>The batch progresses through a workflow that includes:</p>
 * <ul>
 *   <li>Validation of the batch request and business date</li>
 *   <li>Snapshot capture of principal balances and APR rates as of the asOfDate</li>
 *   <li>Generation of Accrual entities for all eligible loans</li>
 *   <li>Monitoring of accrual posting completion</li>
 *   <li>Optional cascade recalculation for back-dated runs</li>
 *   <li>Reconciliation and report generation</li>
 * </ul>
 *
 * <p>For BACKDATED mode runs, a reasonCode is required to document the business justification
 * for the historical correction. Back-dated runs may also trigger cascade recalculations to
 * update subsequent business dates affected by the correction.</p>
 *
 * @see Accrual
 * @see BatchMode
 * @see EODAccrualBatchState
 */
@Data
public class EODAccrualBatch implements CyodaEntity {
    public static final String ENTITY_NAME = "EODAccrualBatch";
    public static final Integer ENTITY_VERSION = 1;

    /**
     * Unique identifier for this batch run
     */
    @JsonProperty("batchId")
    private UUID batchId;

    /**
     * Business date for which accruals are being calculated.
     * Must be a valid business day per the configured calendar.
     */
    @JsonProperty("asOfDate")
    private LocalDate asOfDate;

    /**
     * Mode of the batch run: TODAY for normal daily runs, BACKDATED for historical corrections
     */
    @JsonProperty("mode")
    private BatchMode mode;

    /**
     * User ID of the person who initiated this batch run
     */
    @JsonProperty("initiatedBy")
    private String initiatedBy;

    /**
     * Business reason code for the batch run.
     * Required when mode is BACKDATED to document the justification for the historical correction.
     * Optional for TODAY mode runs.
     */
    @JsonProperty("reasonCode")
    private String reasonCode;

    /**
     * Optional filter criteria to limit which loans are included in this batch.
     * If null or empty, all eligible loans will be processed.
     */
    @JsonProperty("loanFilter")
    private LoanFilter loanFilter;

    /**
     * GL period status for the asOfDate (OPEN or CLOSED).
     * Determined during the snapshot phase. If CLOSED, accruals will be flagged
     * as prior-period adjustments.
     */
    @JsonProperty("periodStatus")
    private PeriodStatus periodStatus;

    /**
     * For back-dated runs, the date from which cascade recalculation should begin.
     * Typically set to the day after asOfDate. Null for TODAY mode runs.
     */
    @JsonProperty("cascadeFromDate")
    private LocalDate cascadeFromDate;

    /**
     * Metrics tracking the progress and results of this batch run.
     * Updated as the batch progresses through its lifecycle.
     */
    @JsonProperty("metrics")
    private BatchMetrics metrics;

    /**
     * UUID of the reconciliation report generated upon batch completion.
     * Null until the batch reaches the RECONCILING state.
     */
    @JsonProperty("reportId")
    private UUID reportId;

    /**
     * Validation error tracking
     */
    @JsonProperty("validationErrorReason")
    private String validationErrorReason;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        if (asOfDate == null) {
            return false;
        }

        if (mode == null) {
            return false;
        }

        if (initiatedBy == null || initiatedBy.trim().isEmpty()) {
            return false;
        }

        // Validate that reasonCode is present when mode is BACKDATED
        if (mode == BatchMode.BACKDATED && (reasonCode == null || reasonCode.trim().isEmpty())) {
            return false;
        }

        // Validate that metrics object is initialized
        if (metrics == null) {
            return false;
        }

        return true;
    }
}

