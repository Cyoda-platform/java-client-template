package com.java_template.application.processor;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.entity.party.version_1.Party;
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
import java.util.Arrays;
import java.util.List;

/**
 * ABOUTME: This processor validates new loan applications ensuring all required fields
 * are present, party exists and is active, and loan terms comply with business rules.
 */
@Component
public class ValidateNewLoanProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateNewLoanProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    private static final List<Integer> VALID_TERMS = Arrays.asList(12, 24, 36);
    private static final List<String> VALID_DAY_COUNT_BASIS = Arrays.asList("ACT/365F", "ACT/360", "ACT/365L");

    public ValidateNewLoanProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Loan.class)
                .validate(this::isValidEntityWithMetadata, "Invalid loan entity wrapper")
                .map(this::validateLoanLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Loan> entityWithMetadata) {
        Loan loan = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return loan != null && loan.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Loan> validateLoanLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Validating loan: {}", loan.getLoanId());

        // Validate party exists and is active
        validateParty(loan.getPartyId());

        // Validate loan terms
        validateLoanTerms(loan);

        // Set creation metadata
        loan.setCreatedAt(LocalDateTime.now());
        loan.setUpdatedAt(LocalDateTime.now());

        // Initialize balances
        loan.setOutstandingPrincipal(BigDecimal.ZERO);
        loan.setAccruedInterest(BigDecimal.ZERO);
        loan.setTotalInterestReceivable(BigDecimal.ZERO);

        logger.info("Loan {} validated successfully", loan.getLoanId());

        return entityWithMetadata;
    }

    private void validateParty(String partyId) {
        try {
            ModelSpec partyModelSpec = new ModelSpec()
                    .withName(Party.ENTITY_NAME)
                    .withVersion(Party.ENTITY_VERSION);
            
            EntityWithMetadata<Party> partyResponse = entityService.findByBusinessId(
                    partyModelSpec, partyId, "partyId", Party.class);

            if (partyResponse == null) {
                throw new IllegalArgumentException("Party not found: " + partyId);
            }

            String partyState = partyResponse.metadata().getState();
            if (!"active".equalsIgnoreCase(partyState)) {
                throw new IllegalArgumentException("Party is not active: " + partyId + " (state: " + partyState + ")");
            }

            logger.debug("Party validation passed for: {}", partyId);
        } catch (Exception e) {
            logger.error("Party validation failed for: {}", partyId, e);
            throw new IllegalArgumentException("Invalid party reference: " + partyId, e);
        }
    }

    private void validateLoanTerms(Loan loan) {
        // Validate term months
        if (!VALID_TERMS.contains(loan.getTermMonths())) {
            throw new IllegalArgumentException("Invalid term months. Must be 12, 24, or 36. Got: " + loan.getTermMonths());
        }

        // Validate day count basis
        if (!VALID_DAY_COUNT_BASIS.contains(loan.getDayCountBasis())) {
            throw new IllegalArgumentException("Invalid day count basis. Must be ACT/365F, ACT/360, or ACT/365L. Got: " + loan.getDayCountBasis());
        }

        // Validate APR
        if (loan.getApr().compareTo(BigDecimal.ZERO) <= 0 || loan.getApr().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("APR must be between 0 and 100. Got: " + loan.getApr());
        }

        // Validate principal amount
        if (loan.getPrincipalAmount().compareTo(new BigDecimal("1000")) < 0) {
            throw new IllegalArgumentException("Principal amount must be at least 1000. Got: " + loan.getPrincipalAmount());
        }

        // Validate repayment day
        if (loan.getRepaymentDay() < 1 || loan.getRepaymentDay() > 31) {
            throw new IllegalArgumentException("Repayment day must be between 1 and 31. Got: " + loan.getRepaymentDay());
        }

        logger.debug("Loan terms validation passed for: {}", loan.getLoanId());
    }
}
