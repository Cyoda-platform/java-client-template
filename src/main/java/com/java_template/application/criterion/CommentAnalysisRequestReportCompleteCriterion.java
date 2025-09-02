package com.java_template.application.criterion;

import com.java_template.application.entity.commentanalysisrequest.version_1.CommentAnalysisRequest;
import com.java_template.application.entity.emailreport.version_1.EmailReport;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class CommentAnalysisRequestReportCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    public CommentAnalysisRequestReportCompleteCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(CommentAnalysisRequest.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CommentAnalysisRequest> context) {
        CommentAnalysisRequest entity = context.entity();
        
        try {
            // Get EmailReport entity by requestId
            Condition requestIdCondition = Condition.of("$.requestId", "EQUALS", entity.getRequestId());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(requestIdCondition));

            Optional<com.java_template.common.dto.EntityResponse<EmailReport>> reportResponse = 
                entityService.getFirstItemByCondition(
                    EmailReport.class, 
                    EmailReport.ENTITY_NAME, 
                    EmailReport.ENTITY_VERSION, 
                    condition, 
                    true
                );

            // Check if entity exists
            if (!reportResponse.isPresent()) {
                return EvaluationOutcome.fail("EmailReport not found for requestId: " + entity.getRequestId(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            EmailReport report = reportResponse.get().getData();
            String entityState = reportResponse.get().getMetadata().getState();

            // Check if entity state is "sending"
            if (!"sending".equals(entityState)) {
                return EvaluationOutcome.fail("EmailReport state is not sending, current state: " + entityState, 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if email content is prepared
            if (report.getSubject() == null || report.getSubject().trim().isEmpty()) {
                return EvaluationOutcome.fail("EmailReport subject is empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (report.getHtmlContent() == null || report.getHtmlContent().trim().isEmpty()) {
                return EvaluationOutcome.fail("EmailReport htmlContent is empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (report.getTextContent() == null || report.getTextContent().trim().isEmpty()) {
                return EvaluationOutcome.fail("EmailReport textContent is empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (report.getRecipientEmail() == null || !isValidEmail(report.getRecipientEmail())) {
                return EvaluationOutcome.fail("EmailReport recipientEmail is not valid", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            logger.info("EmailReport is ready for sending for requestId: {}", entity.getRequestId());
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.error("Error checking report completion for requestId: {}", entity.getRequestId(), e);
            return EvaluationOutcome.fail("Error checking report completion: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
