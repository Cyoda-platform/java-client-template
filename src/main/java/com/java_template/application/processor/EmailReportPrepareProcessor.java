package com.java_template.application.processor;

import com.java_template.application.entity.email_report.version_1.EmailReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * EmailReportPrepareProcessor - Generate email content from analysis data
 * 
 * Transition: none → prepared
 * Purpose: Generate email content from analysis data
 */
@Component
public class EmailReportPrepareProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportPrepareProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailReportPrepareProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailReport preparation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email report entity")
                .map(this::processReportPreparation)
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
     * Main business logic for report preparation
     */
    private EntityWithMetadata<EmailReport> processReportPreparation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailReport> context) {

        EntityWithMetadata<EmailReport> entityWithMetadata = context.entityResponse();
        EmailReport report = entityWithMetadata.entity();

        logger.debug("Preparing email report: {}", report.getReportId());

        // Validate that all required fields are present
        if (report.getReportContent() == null || report.getReportContent().trim().isEmpty()) {
            logger.error("Email report content is null or empty for report: {}", report.getReportId());
            throw new IllegalStateException("Email report content cannot be null or empty");
        }

        if (report.getRecipientEmail() == null || report.getRecipientEmail().trim().isEmpty()) {
            logger.error("Recipient email is null or empty for report: {}", report.getReportId());
            throw new IllegalStateException("Recipient email cannot be null or empty");
        }

        if (report.getSubject() == null || report.getSubject().trim().isEmpty()) {
            logger.error("Email subject is null or empty for report: {}", report.getReportId());
            throw new IllegalStateException("Email subject cannot be null or empty");
        }

        // Basic email format validation
        if (!report.getRecipientEmail().contains("@") || !report.getRecipientEmail().contains(".")) {
            logger.error("Invalid email format for report: {}", report.getReportId());
            throw new IllegalStateException("Invalid email format");
        }

        logger.info("Email report prepared: {}", report.getReportId());

        return entityWithMetadata;
    }
}
