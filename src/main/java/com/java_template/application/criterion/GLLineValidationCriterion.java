package com.java_template.application.criterion;

import com.java_template.application.entity.gl_line.version_1.GLLine;
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

/**
 * ABOUTME: This criterion validates GLLine entities during creation,
 * ensuring all required fields are present and business rules are met.
 */
@Component
public class GLLineValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public GLLineValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking GLLine validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(GLLine.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<GLLine> context) {
        GLLine glLine = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (glLine == null) {
            logger.warn("GLLine entity is null");
            return EvaluationOutcome.fail("GLLine entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!glLine.isValid()) {
            logger.warn("GLLine entity is not valid: {}", glLine.getGlLineId());
            return EvaluationOutcome.fail("GLLine entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate required fields
        if (glLine.getGlLineId() == null || glLine.getGlLineId().trim().isEmpty()) {
            logger.warn("GL Line ID is required");
            return EvaluationOutcome.fail("GL Line ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (glLine.getBatchId() == null || glLine.getBatchId().trim().isEmpty()) {
            logger.warn("Batch ID is required for GL line: {}", glLine.getGlLineId());
            return EvaluationOutcome.fail("Batch ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (glLine.getGlAccount() == null || glLine.getGlAccount().trim().isEmpty()) {
            logger.warn("GL Account is required for GL line: {}", glLine.getGlLineId());
            return EvaluationOutcome.fail("GL Account is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (glLine.getDescription() == null || glLine.getDescription().trim().isEmpty()) {
            logger.warn("Description is required for GL line: {}", glLine.getGlLineId());
            return EvaluationOutcome.fail("Description is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate type is DEBIT or CREDIT
        if (glLine.getType() == null || 
            (!"DEBIT".equals(glLine.getType()) && !"CREDIT".equals(glLine.getType()))) {
            logger.warn("Invalid type for GL line {}: {}", glLine.getGlLineId(), glLine.getType());
            return EvaluationOutcome.fail("Type must be DEBIT or CREDIT", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate amount is positive
        if (glLine.getAmount() == null || glLine.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Invalid amount for GL line {}: {}", glLine.getGlLineId(), glLine.getAmount());
            return EvaluationOutcome.fail("Amount must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}

