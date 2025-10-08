package com.java_template.application.criterion;

import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ABOUTME: This criterion validates SettlementQuote entities during creation,
 * ensuring all required fields are present and business rules are met.
 */
@Component
public class SettlementQuoteValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SettlementQuoteValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking SettlementQuote validation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(SettlementQuote.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    public EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<SettlementQuote> context) {
        SettlementQuote quote = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (quote == null) {
            logger.warn("SettlementQuote entity is null");
            return EvaluationOutcome.fail("SettlementQuote entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!quote.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("SettlementQuote entity is not valid: {}", quote.getQuoteId());
            return EvaluationOutcome.fail("SettlementQuote entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate required fields
        if (quote.getQuoteId() == null || quote.getQuoteId().trim().isEmpty()) {
            logger.warn("Quote ID is required");
            return EvaluationOutcome.fail("Quote ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (quote.getLoanId() == null || quote.getLoanId().trim().isEmpty()) {
            logger.warn("Loan ID is required for quote: {}", quote.getQuoteId());
            return EvaluationOutcome.fail("Loan ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (quote.getRequestedBy() == null || quote.getRequestedBy().trim().isEmpty()) {
            logger.warn("Requested by is required for quote: {}", quote.getQuoteId());
            return EvaluationOutcome.fail("Requested by is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate settlement date is not null
        if (quote.getSettlementDate() == null) {
            logger.warn("Settlement date is required for quote: {}", quote.getQuoteId());
            return EvaluationOutcome.fail("Settlement date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate expiration date is not null
        if (quote.getExpirationDate() == null) {
            logger.warn("Expiration date is required for quote: {}", quote.getQuoteId());
            return EvaluationOutcome.fail("Expiration date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate expiration date is after settlement date
        if (quote.getExpirationDate().isBefore(quote.getSettlementDate())) {
            logger.warn("Expiration date {} is before settlement date {} for quote: {}",
                quote.getExpirationDate(), quote.getSettlementDate(), quote.getQuoteId());
            return EvaluationOutcome.fail("Expiration date must be after settlement date", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate settlement date is not in the past
        if (quote.getSettlementDate().isBefore(LocalDate.now())) {
            logger.warn("Settlement date {} is in the past for quote: {}",
                quote.getSettlementDate(), quote.getQuoteId());
            return EvaluationOutcome.fail("Settlement date cannot be in the past", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate total amount due is positive
        if (quote.getTotalAmountDue() == null || quote.getTotalAmountDue().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Invalid total amount due for quote {}: {}",
                quote.getQuoteId(), quote.getTotalAmountDue());
            return EvaluationOutcome.fail("Total amount due must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate currency is provided
        if (quote.getCurrency() == null || quote.getCurrency().trim().isEmpty()) {
            logger.warn("Currency is required for quote: {}", quote.getQuoteId());
            return EvaluationOutcome.fail("Currency is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate currency format (3-letter ISO code)
        String currency = quote.getCurrency().trim().toUpperCase();
        if (currency.length() != 3 || !currency.matches("^[A-Z]{3}$")) {
            logger.warn("Invalid currency format for quote {}: {}", quote.getQuoteId(), currency);
            return EvaluationOutcome.fail("Currency must be a 3-letter ISO code", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}

