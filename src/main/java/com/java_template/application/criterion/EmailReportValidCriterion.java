package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment_analysis.version_1.CommentAnalysis;
import com.java_template.application.entity.email_report.version_1.EmailReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * EmailReportValidCriterion - Validate email report is ready for delivery
 * 
 * Transition: prepared → sending (send_email)
 * Purpose: Validate email report is ready for delivery
 */
@Component
public class EmailReportValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Basic email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    public EmailReportValidCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailReport validity criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailReport.class, this::validateEmailReport)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for email report
     */
    private EvaluationOutcome validateEmailReport(CriterionSerializer.CriterionEntityEvaluationContext<EmailReport> context) {
        EmailReport report = context.entityWithMetadata().entity();

        // Check if report entity is null
        if (report == null) {
            logger.warn("EmailReport entity is null");
            return EvaluationOutcome.fail("Email report entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!report.isValid()) {
            logger.warn("EmailReport entity is not valid");
            return EvaluationOutcome.fail("Email report entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate required fields
        if (report.getRecipientEmail() == null || report.getRecipientEmail().trim().isEmpty()) {
            return EvaluationOutcome.fail("Recipient email cannot be null or empty", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (report.getSubject() == null || report.getSubject().trim().isEmpty()) {
            return EvaluationOutcome.fail("Email subject cannot be null or empty", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (report.getReportContent() == null || report.getReportContent().trim().isEmpty()) {
            return EvaluationOutcome.fail("Email content cannot be null or empty", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate email format
        if (!isValidEmailFormat(report.getRecipientEmail())) {
            return EvaluationOutcome.fail(
                "Invalid email format: " + report.getRecipientEmail(),
                StandardEvalReasonCategories.VALIDATION_FAILURE
            );
        }

        // Check content length (not too short)
        if (report.getReportContent().length() < 50) {
            return EvaluationOutcome.fail(
                "Email content too short. Minimum 50 characters required, found: " + report.getReportContent().length(),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Validate related analysis exists and is completed
        String analysisId = report.getAnalysisId();
        if (analysisId == null || analysisId.trim().isEmpty()) {
            return EvaluationOutcome.fail("Analysis ID cannot be null or empty", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        try {
            ModelSpec analysisModelSpec = new ModelSpec()
                    .withName(CommentAnalysis.ENTITY_NAME)
                    .withVersion(CommentAnalysis.ENTITY_VERSION);

            SimpleCondition analysisIdCondition = new SimpleCondition()
                    .withJsonPath("$.analysisId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(analysisId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(analysisIdCondition));

            List<EntityWithMetadata<CommentAnalysis>> analyses = 
                    entityService.search(analysisModelSpec, condition, CommentAnalysis.class);

            if (analyses.isEmpty()) {
                return EvaluationOutcome.fail(
                    "Related CommentAnalysis not found for analysisId: " + analysisId,
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE
                );
            }

            EntityWithMetadata<CommentAnalysis> analysisWithMetadata = analyses.get(0);
            String analysisState = analysisWithMetadata.metadata().getState();
            
            if (!"completed".equals(analysisState)) {
                return EvaluationOutcome.fail(
                    "Related CommentAnalysis is not completed. Current state: " + analysisState,
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }

            logger.debug("EmailReport validation passed for report: {}", report.getReportId());
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error validating related CommentAnalysis for analysisId: {}", analysisId, e);
            return EvaluationOutcome.fail(
                "Error validating related analysis: " + e.getMessage(),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }
    }

    /**
     * Validate email format using regex pattern
     */
    private boolean isValidEmailFormat(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
