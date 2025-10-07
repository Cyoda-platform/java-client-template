package com.java_template.application.processor.eod_batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.AccrualState;
import com.java_template.application.entity.accrual.version_1.BatchMetrics;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.application.entity.accrual.version_1.LoanFilter;
import com.java_template.application.entity.accrual.version_1.PeriodStatus;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Processor to fan-out Accruals for ACTIVE loans on AsOfDate.
 *
 * <p>This processor:</p>
 * <ul>
 *   <li>Queries all loans matching the batch's loanFilter</li>
 *   <li>Filters loans that were ACTIVE on asOfDate</li>
 *   <li>For each eligible loan, creates a new Accrual entity with state NEW</li>
 *   <li>Sets accrual's runId to the batch's batchId</li>
 *   <li>Sets accrual's asOfDate to the batch's asOfDate</li>
 *   <li>Sets accrual's priorPeriodFlag based on batch's periodStatus</li>
 *   <li>Updates batch metrics with eligibleLoans count</li>
 * </ul>
 *
 * <p>Execution Mode: ASYNC_NEW_TX</p>
 * <p>Calculation Nodes Tags: accruals</p>
 */
@Component
public class SpawnAccrualsForEligibleLoansProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SpawnAccrualsForEligibleLoansProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpawnAccrualsForEligibleLoansProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SpawnAccrualsForEligibleLoans for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(EODAccrualBatch.class)
            .validate(this::isValidEntityWithMetadata, "Invalid batch entity")
            .map(this::spawnAccrualsLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SpawnAccrualsForEligibleLoans".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EODAccrualBatch> entityWithMetadata) {
        EODAccrualBatch batch = entityWithMetadata.entity();
        return batch != null && batch.isValid() && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic to spawn accruals for eligible loans.
     *
     * <p>CRITICAL LIMITATIONS:</p>
     * <ul>
     *   <li>✅ ALLOWED: Read current batch data</li>
     *   <li>✅ ALLOWED: Create new Accrual entities via EntityService</li>
     *   <li>✅ ALLOWED: Update batch metrics</li>
     *   <li>❌ FORBIDDEN: Update current batch state/transitions</li>
     * </ul>
     */
    private EntityWithMetadata<EODAccrualBatch> spawnAccrualsLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EODAccrualBatch> context) {

        EntityWithMetadata<EODAccrualBatch> entityWithMetadata = context.entityResponse();
        EODAccrualBatch batch = entityWithMetadata.entity();

        UUID batchId = batch.getBatchId();
        LocalDate asOfDate = batch.getAsOfDate();
        LoanFilter loanFilter = batch.getLoanFilter();

        // Determine priorPeriodFlag based on period status
        boolean priorPeriodFlag = (batch.getPeriodStatus() == PeriodStatus.CLOSED);

        if (asOfDate == null) {
            logger.error("AsOfDate is null for batch: {}", batchId);
            throw new IllegalStateException("AsOfDate is required to spawn accruals");
        }

        logger.debug("Spawning accruals for batch: {} with asOfDate: {}", batchId, asOfDate);

        // Query eligible loans
        List<Loan> eligibleLoans = queryEligibleLoans(asOfDate, loanFilter);

        logger.info("Found {} eligible loans for batch: {}", eligibleLoans.size(), batchId);

        // Create accruals for each eligible loan
        int accrualsCreated = 0;
        for (Loan loan : eligibleLoans) {
            try {
                createAccrualForLoan(loan, batchId, asOfDate, priorPeriodFlag);
                accrualsCreated++;
            } catch (Exception e) {
                logger.error("Failed to create accrual for loan: {}", loan.getLoanId(), e);
                // Continue processing other loans
            }
        }

        // Update batch metrics
        BatchMetrics metrics = batch.getMetrics();
        if (metrics == null) {
            metrics = new BatchMetrics();
            batch.setMetrics(metrics);
        }
        metrics.setEligibleLoans(eligibleLoans.size());
        metrics.setAccrualsCreated(accrualsCreated);

        logger.info("Batch {} spawned {} accruals for {} eligible loans",
            batchId, accrualsCreated, eligibleLoans.size());

        return entityWithMetadata;
    }

    /**
     * Queries loans that are eligible for accrual on the given date.
     *
     * @param asOfDate The date to check loan eligibility
     * @param loanFilter Optional filter criteria for loans
     * @return List of eligible loans
     */
    private List<Loan> queryEligibleLoans(LocalDate asOfDate, LoanFilter loanFilter) {
        ModelSpec loanModelSpec = new ModelSpec()
            .withName(Loan.ENTITY_NAME)
            .withVersion(Loan.ENTITY_VERSION);

        // Query all loans
        List<EntityWithMetadata<Loan>> loansWithMetadata = 
            entityService.findAll(loanModelSpec, Loan.class);

        List<Loan> loans = loansWithMetadata.stream()
            .map(EntityWithMetadata::entity)
            .toList();

        // Apply loan filter if present
        if (loanFilter != null) {
            loans = applyLoanFilter(loans, loanFilter);
        }

        // Filter for loans that were ACTIVE on asOfDate
        List<Loan> eligibleLoans = new ArrayList<>();
        for (Loan loan : loans) {
            if (isLoanActiveOnDate(loan, asOfDate)) {
                eligibleLoans.add(loan);
            }
        }

        return eligibleLoans;
    }

    /**
     * Applies loan filter criteria to the list of loans.
     */
    private List<Loan> applyLoanFilter(List<Loan> loans, LoanFilter loanFilter) {
        List<UUID> loanIds = loanFilter.getLoanIds();
        List<String> productCodes = loanFilter.getProductCodes();

        if (loanIds != null && !loanIds.isEmpty()) {
            // Convert loan.getLoanId() (String) to UUID for comparison
            loans = loans.stream()
                .filter(loan -> {
                    try {
                        UUID loanUuid = UUID.fromString(loan.getLoanId());
                        return loanIds.contains(loanUuid);
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .toList();
        }

        if (productCodes != null && !productCodes.isEmpty()) {
            // TODO: Add productCode field to Loan entity
            // For now, skip product code filtering
            logger.warn("Product code filtering not yet implemented - Loan entity lacks productCode field");
        }

        return loans;
    }

    /**
     * Determines if a loan was ACTIVE on the given date.
     *
     * <p>A loan is considered active if:</p>
     * <ul>
     *   <li>The asOfDate is on or after the funding date</li>
     *   <li>The asOfDate is before the maturity date</li>
     * </ul>
     */
    private boolean isLoanActiveOnDate(Loan loan, LocalDate asOfDate) {
        LocalDate fundingDate = loan.getFundingDate();
        LocalDate maturityDate = loan.getMaturityDate();

        if (fundingDate == null || maturityDate == null) {
            return false;
        }

        // Loan is active if asOfDate is on or after funding and before maturity
        return !asOfDate.isBefore(fundingDate) && asOfDate.isBefore(maturityDate);
    }

    /**
     * Creates a new Accrual entity for the given loan.
     */
    private void createAccrualForLoan(Loan loan, UUID runId, LocalDate asOfDate, boolean priorPeriodFlag) {
        // Create new accrual
        Accrual accrual = new Accrual();
        accrual.setAccrualId(UUID.randomUUID().toString());
        accrual.setLoanId(loan.getLoanId());
        accrual.setAsOfDate(asOfDate);
        accrual.setCurrency(loan.getCurrency());
        accrual.setRunId(runId.toString());
        accrual.setPriorPeriodFlag(priorPeriodFlag);

        // TODO: Get day count convention from loan
        // For now, use a default value
        // accrual.setDayCountConvention(loan.getDayCountConvention());

        // Initialize with zero amounts (will be calculated by workflow processors)
        accrual.setDayCountFraction(BigDecimal.ZERO);
        accrual.setInterestAmount(BigDecimal.ZERO);

        // Set initial state
        accrual.setState(AccrualState.NEW);

        // Create the accrual entity
        ModelSpec accrualModelSpec = new ModelSpec()
            .withName(Accrual.ENTITY_NAME)
            .withVersion(Accrual.ENTITY_VERSION);

        entityService.create(accrual);

        logger.debug("Created accrual {} for loan {} with runId {}",
            accrual.getAccrualId(), loan.getLoanId(), runId);
    }
}

