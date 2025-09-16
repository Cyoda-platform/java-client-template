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

import java.time.LocalDateTime;

/**
 * EmailReportRetryProcessor - Prepare failed email for retry
 * 
 * Transition: failed → retry
 * Purpose: Prepare failed email for retry
 */
@Component
public class EmailReportRetryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportRetryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailReportRetryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailReport retry preparation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email report entity")
                .map(this::processEmailRetry)
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
     * Main business logic for email retry preparation
     */
    private EntityWithMetadata<EmailReport> processEmailRetry(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailReport> context) {

        EntityWithMetadata<EmailReport> entityWithMetadata = context.entityResponse();
        EmailReport report = entityWithMetadata.entity();

        logger.debug("Preparing email retry for report: {}", report.getReportId());

        // Set status to retry
        report.setDeliveryStatus("RETRY");
        
        // Update last retry timestamp
        report.setLastRetryAt(LocalDateTime.now());
        
        // Clear previous error message to start fresh
        report.setErrorMessage(null);

        logger.info("Email prepared for retry: {}, Attempt: {}", 
                   report.getReportId(), report.getRetryCount());

        return entityWithMetadata;
    }
}
