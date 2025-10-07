package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.entity.payment.version_1.Payment;
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
import java.util.List;

/**
 * ABOUTME: This processor allocates payment funds according to the waterfall rules:
 * 1. Accrued Interest, 2. Fees (future), 3. Principal, 4. Excess funds
 */
@Component
public class AllocatePaymentFunds implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AllocatePaymentFunds.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AllocatePaymentFunds(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        Payment entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Payment> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Allocating payment funds for payment: {} amount: {}",
                    payment.getPaymentId(), payment.getPaymentAmount());

        // Get the associated loan
        Loan loan = getLoanForPayment(payment.getLoanId());
        if (loan == null) {
            throw new IllegalStateException("Cannot find loan for payment: " + payment.getLoanId());
        }

        // Initialize allocation if not present
        if (payment.getAllocation() == null) {
            payment.setAllocation(new Payment.PaymentAllocation());
        }

        // Apply waterfall allocation
        BigDecimal remainingAmount = payment.getPaymentAmount();
        Payment.PaymentAllocation allocation = payment.getAllocation();

        // Step 1: Allocate to accrued interest
        BigDecimal accruedInterest = loan.getAccruedInterest() != null ? loan.getAccruedInterest() : BigDecimal.ZERO;
        BigDecimal interestAllocation = remainingAmount.min(accruedInterest);
        allocation.setInterestAllocated(interestAllocation);
        remainingAmount = remainingAmount.subtract(interestAllocation);

        logger.debug("Interest allocation: {} (remaining: {})", interestAllocation, remainingAmount);

        // Step 2: Allocate to fees (placeholder for future implementation)
        allocation.setFeesAllocated(BigDecimal.ZERO);

        // Step 3: Allocate to principal
        BigDecimal outstandingPrincipal = loan.getOutstandingPrincipal() != null ? loan.getOutstandingPrincipal() : BigDecimal.ZERO;
        BigDecimal principalAllocation = remainingAmount.min(outstandingPrincipal);
        allocation.setPrincipalAllocated(principalAllocation);
        remainingAmount = remainingAmount.subtract(principalAllocation);

        logger.debug("Principal allocation: {} (remaining: {})", principalAllocation, remainingAmount);

        // Step 4: Handle excess funds
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            allocation.setExcessFunds(remainingAmount);
            logger.info("Excess funds detected: {}", remainingAmount);
        } else {
            allocation.setExcessFunds(BigDecimal.ZERO);
        }

        // Update the loan balances
        updateLoanBalances(loan, allocation);

        logger.info("Payment allocation completed for {}: Interest={}, Principal={}, Excess={}",
                   payment.getPaymentId(), allocation.getInterestAllocated(),
                   allocation.getPrincipalAllocated(), allocation.getExcessFunds());

        return entityWithMetadata;
    }

    private Loan getLoanForPayment(String loanId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            ObjectMapper objectMapper = new ObjectMapper();

            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.loanId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(loanId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<Loan>> loans = entityService.search(modelSpec, groupCondition, Loan.class);

            if (loans.isEmpty()) {
                logger.error("Loan not found: {}", loanId);
                return null;
            }

            return loans.getFirst().entity();

        } catch (Exception e) {
            logger.error("Error retrieving loan: {}", loanId, e);
            return null;
        }
    }

    private void updateLoanBalances(Loan loan, Payment.PaymentAllocation allocation) {
        try {
            // Update accrued interest balance
            BigDecimal newAccruedInterest = loan.getAccruedInterest().subtract(allocation.getInterestAllocated());
            loan.setAccruedInterest(newAccruedInterest.max(BigDecimal.ZERO));

            // Update outstanding principal balance
            BigDecimal newOutstandingPrincipal = loan.getOutstandingPrincipal().subtract(allocation.getPrincipalAllocated());
            loan.setOutstandingPrincipal(newOutstandingPrincipal.max(BigDecimal.ZERO));

            // Find the loan entity and update it
            ModelSpec modelSpec = new ModelSpec().withName(Loan.ENTITY_NAME).withVersion(Loan.ENTITY_VERSION);
            ObjectMapper objectMapper = new ObjectMapper();

            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.loanId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(loan.getLoanId()));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<Loan>> loans = entityService.search(modelSpec, groupCondition, Loan.class);

            if (!loans.isEmpty()) {
                EntityWithMetadata<Loan> loanWithMetadata = loans.getFirst();
                // Update the loan without transition (loop back to same state)
                entityService.update(loanWithMetadata.metadata().getId(), loan, null);

                logger.debug("Updated loan balances: AccruedInterest={}, OutstandingPrincipal={}",
                           newAccruedInterest, newOutstandingPrincipal);
            }

        } catch (Exception e) {
            logger.error("Error updating loan balances for loan: {}", loan.getLoanId(), e);
            throw new RuntimeException("Failed to update loan balances", e);
        }
    }
}
