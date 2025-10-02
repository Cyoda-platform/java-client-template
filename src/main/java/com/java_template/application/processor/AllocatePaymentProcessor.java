package com.java_template.application.processor;

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
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ABOUTME: This processor allocates payment amounts to interest, fees, and principal
 * following the standard waterfall allocation rules for loan payments.
 */
@Component
public class AllocatePaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AllocatePaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AllocatePaymentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::allocatePaymentLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        Payment payment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return payment != null && payment.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Payment> allocatePaymentLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Allocating payment: {} for loan: {}", payment.getPaymentId(), payment.getLoanId());

        // Get the associated loan
        Loan loan = getLoanForPayment(payment);
        
        // Perform allocation
        Payment.PaymentAllocation allocation = performAllocation(payment, loan);
        payment.setAllocation(allocation);

        // Set allocation timestamp
        payment.setAllocatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Payment {} allocated successfully. Interest: {}, Principal: {}, Unallocated: {}", 
                   payment.getPaymentId(), 
                   allocation.getInterestAmount(),
                   allocation.getPrincipalAmount(),
                   allocation.getUnallocatedAmount());

        return entityWithMetadata;
    }

    private Loan getLoanForPayment(Payment payment) {
        try {
            ModelSpec loanModelSpec = new ModelSpec()
                    .withName(Loan.ENTITY_NAME)
                    .withVersion(Loan.ENTITY_VERSION);
            
            EntityWithMetadata<Loan> loanResponse = entityService.findByBusinessId(
                    loanModelSpec, payment.getLoanId(), "loanId", Loan.class);

            if (loanResponse == null) {
                throw new IllegalArgumentException("Loan not found: " + payment.getLoanId());
            }

            return loanResponse.entity();
        } catch (Exception e) {
            logger.error("Failed to retrieve loan for payment allocation: {}", payment.getLoanId(), e);
            throw new IllegalArgumentException("Cannot allocate payment - loan not found: " + payment.getLoanId(), e);
        }
    }

    private Payment.PaymentAllocation performAllocation(Payment payment, Loan loan) {
        Payment.PaymentAllocation allocation = new Payment.PaymentAllocation();
        
        BigDecimal remainingAmount = payment.getAmount();
        BigDecimal interestDue = loan.getAccruedInterest() != null ? loan.getAccruedInterest() : BigDecimal.ZERO;
        BigDecimal principalOutstanding = loan.getOutstandingPrincipal() != null ? loan.getOutstandingPrincipal() : BigDecimal.ZERO;

        // Step 1: Allocate to interest first
        BigDecimal interestAllocation = remainingAmount.min(interestDue);
        allocation.setInterestAmount(interestAllocation);
        remainingAmount = remainingAmount.subtract(interestAllocation);

        // Step 2: Allocate to fees (assuming no fees for MVP)
        allocation.setFeesAmount(BigDecimal.ZERO);

        // Step 3: Allocate remaining to principal
        BigDecimal principalAllocation = remainingAmount.min(principalOutstanding);
        allocation.setPrincipalAmount(principalAllocation);
        remainingAmount = remainingAmount.subtract(principalAllocation);

        // Step 4: Any remaining amount is unallocated
        allocation.setUnallocatedAmount(remainingAmount);

        // Calculate total allocated
        BigDecimal totalAllocated = interestAllocation.add(principalAllocation);
        allocation.setTotalAllocated(totalAllocated);

        // Add allocation notes
        StringBuilder notes = new StringBuilder();
        notes.append("Waterfall allocation: ");
        if (interestAllocation.compareTo(BigDecimal.ZERO) > 0) {
            notes.append("Interest: ").append(interestAllocation).append("; ");
        }
        if (principalAllocation.compareTo(BigDecimal.ZERO) > 0) {
            notes.append("Principal: ").append(principalAllocation).append("; ");
        }
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            notes.append("Unallocated: ").append(remainingAmount).append("; ");
        }
        allocation.setAllocationNotes(notes.toString());

        return allocation;
    }
}
