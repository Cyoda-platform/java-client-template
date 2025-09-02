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

@Component
public class CommentAnalysisRequestEmailSentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public CommentAnalysisRequestEmailSentCriterion(SerializerFactory serializerFactory, EntityService entityService) {
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

            // Check if entity state is "sent"
            if (!"sent".equals(entityState)) {
                return EvaluationOutcome.fail("EmailReport state is not sent, current state: " + entityState, 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if emailStatus equals "SENT"
            if (!"SENT".equals(report.getEmailStatus())) {
                return EvaluationOutcome.fail("EmailReport emailStatus is not SENT, current status: " + report.getEmailStatus(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if sentAt timestamp is set
            if (report.getSentAt() == null) {
                return EvaluationOutcome.fail("EmailReport sentAt timestamp is not set", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            logger.info("Email has been successfully sent for requestId: {}", entity.getRequestId());
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.error("Error checking email sent status for requestId: {}", entity.getRequestId(), e);
            return EvaluationOutcome.fail("Error checking email sent status: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
