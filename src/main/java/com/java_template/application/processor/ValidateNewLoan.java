package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

/**
 * ABOUTME: This processor validates a new Loan entity during creation,
 * ensuring all required fields are present, business rules are met, and referenced parties exist.
 */
@Component
public class ValidateNewLoan implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateNewLoan.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ValidateNewLoan(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Loan> entityWithMetadata) {
        Loan entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        boolean result = entity != null && entity.isValid() && technicalId != null;
        return result;
    }

    private EntityWithMetadata<Loan> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {

        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        logger.debug("Validating new loan: {}", loan.getLoanId());

        // Validate term is one of allowed values
        if (loan.getTermMonths() == null ||
            (loan.getTermMonths() != 12 && loan.getTermMonths() != 24 && loan.getTermMonths() != 36)) {
            throw new IllegalArgumentException("Term must be 12, 24, or 36 months");
        }

        // Validate APR is within reasonable range (1% to 25%)
        if (loan.getApr() == null ||
            loan.getApr().compareTo(new BigDecimal("0.01")) < 0 ||
            loan.getApr().compareTo(new BigDecimal("0.25")) > 0) {
            throw new IllegalArgumentException("APR must be between 1% and 25%");
        }

        // Validate principal amount is positive
        if (loan.getPrincipalAmount() == null || loan.getPrincipalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Principal amount must be positive");
        }

        // Validate funding date is not in the past
        if (loan.getFundingDate() != null && loan.getFundingDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Funding date cannot be in the past");
        }

        // Calculate maturity date if not provided
        if (loan.getMaturityDate() == null && loan.getFundingDate() != null) {
            loan.setMaturityDate(loan.getFundingDate().plusMonths(loan.getTermMonths()));
        }

        // Validate that the referenced party exists and is active
        validatePartyExists(loan.getPartyId());

        // Set default values
        if (loan.getCurrency() == null || loan.getCurrency().trim().isEmpty()) {
            loan.setCurrency("GBP");
        }

        if (loan.getDayCountBasis() == null || loan.getDayCountBasis().trim().isEmpty()) {
            loan.setDayCountBasis("ACT/365");
        }

        // Initialize financial balances to zero
        loan.setOutstandingPrincipal(BigDecimal.ZERO);
        loan.setAccruedInterest(BigDecimal.ZERO);

        logger.info("Loan {} validation completed successfully", loan.getLoanId());

        return entityWithMetadata;
    }

    private void validatePartyExists(String partyId) {
        if (partyId == null || partyId.trim().isEmpty()) {
            throw new IllegalArgumentException("Party ID is required");
        }

        ModelSpec modelSpec = new ModelSpec().withName(Party.ENTITY_NAME).withVersion(Party.ENTITY_VERSION);
        ObjectMapper objectMapper = new ObjectMapper();

        SimpleCondition condition = new SimpleCondition()
                .withJsonPath("$.partyId")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(partyId));

        GroupCondition groupCondition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(condition));

        List<EntityWithMetadata<Party>> parties = entityService.search(modelSpec, groupCondition, Party.class);

        if (parties.isEmpty()) {
            throw new IllegalArgumentException("Referenced party does not exist: " + partyId);
        }

        EntityWithMetadata<Party> partyWithMetadata = parties.getFirst();
        String partyState = partyWithMetadata.metadata().getState();
        if (!"active".equals(partyState)) {
            throw new IllegalArgumentException("Referenced party is not active: " + partyId + " (state: " + partyState + ")");
        }

        logger.debug("Party validation successful for: {}", partyId);
    }
}
