package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment_analysis.version_1.CommentAnalysis;
import com.java_template.application.entity.email_report.version_1.EmailReport;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmailReportDeliveredProcessor - Mark email as successfully sent
 * 
 * Transition: sending → sent
 * Purpose: Mark email as successfully sent and update related CommentAnalysis
 */
@Component
public class EmailReportDeliveredProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportDeliveredProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailReportDeliveredProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailReport delivery confirmation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email report entity")
                .map(this::processEmailDelivery)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EmailReport> entityWithMetadata) {
        EmailReport entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for email delivery confirmation
     */
    private EntityWithMetadata<EmailReport> processEmailDelivery(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailReport> context) {

        EntityWithMetadata<EmailReport> entityWithMetadata = context.entityResponse();
        EmailReport report = entityWithMetadata.entity();

        logger.debug("Confirming email delivery for report: {}", report.getReportId());

        // Mark email as successfully sent
        report.setDeliveryStatus("SENT");
        report.setSentAt(LocalDateTime.now());
        report.setErrorMessage(null); // Clear any previous error messages

        // Update related CommentAnalysis to mark email as sent
        updateRelatedCommentAnalysis(report);

        logger.info("Email delivered successfully: {}", report.getReportId());

        return entityWithMetadata;
    }

    /**
     * Update the related CommentAnalysis to mark email as sent
     */
    private void updateRelatedCommentAnalysis(EmailReport report) {
        try {
            // Search for the related CommentAnalysis
            ModelSpec analysisModelSpec = new ModelSpec()
                    .withName(CommentAnalysis.ENTITY_NAME)
                    .withVersion(CommentAnalysis.ENTITY_VERSION);

            SimpleCondition analysisIdCondition = new SimpleCondition()
                    .withJsonPath("$.analysisId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(report.getAnalysisId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(analysisIdCondition));

            List<EntityWithMetadata<CommentAnalysis>> analyses = 
                    entityService.search(analysisModelSpec, condition, CommentAnalysis.class);

            if (!analyses.isEmpty()) {
                EntityWithMetadata<CommentAnalysis> analysisWithMetadata = analyses.get(0);
                CommentAnalysis analysis = analysisWithMetadata.entity();
                
                // Update email sent status
                analysis.setEmailSent(true);
                analysis.setEmailSentAt(LocalDateTime.now());
                
                // Update the analysis entity (no transition needed)
                entityService.update(analysisWithMetadata.metadata().getId(), analysis, null);
                
                logger.info("Updated CommentAnalysis {} to mark email as sent", analysis.getAnalysisId());
            } else {
                logger.warn("No CommentAnalysis found for analysisId: {}", report.getAnalysisId());
            }
            
        } catch (Exception e) {
            logger.error("Failed to update related CommentAnalysis for report: {}", report.getReportId(), e);
            // Don't throw exception here as the email was successfully sent
            // The CommentAnalysis update is a secondary operation
        }
    }
}
