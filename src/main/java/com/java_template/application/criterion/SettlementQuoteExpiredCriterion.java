package com.java_template.application.criterion;

import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
import com.java_template.common.serializer.*;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * ABOUTME: This criterion checks if the settlement quote has expired (expiration date is in the past).
 * Used to automatically expire settlement quotes when their expiration date passes.
 */
@Component
public class SettlementQuoteExpiredCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SettlementQuoteExpiredCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if settlement quote has expired for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(SettlementQuote.class, this::checkQuoteExpired)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome checkQuoteExpired(CriterionSerializer.CriterionEntityEvaluationContext<SettlementQuote> context) {
        SettlementQuote quote = context.entityWithMetadata().entity();

        if (quote.getExpirationDate() == null) {
            logger.debug("Settlement quote has no expiration date set");
            return EvaluationOutcome.fail("Expiration date is not set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        LocalDate today = LocalDate.now();
        boolean isExpired = quote.getExpirationDate().isBefore(today);

        if (isExpired) {
            logger.debug("Settlement quote expiration date {} has passed (today: {})", quote.getExpirationDate(), today);
            return EvaluationOutcome.success();
        } else {
            logger.debug("Settlement quote expiration date {} has not passed yet (today: {})", quote.getExpirationDate(), today);
            return EvaluationOutcome.fail(
                String.format("Expiration date %s has not passed yet", quote.getExpirationDate()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }
    }
}

