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
 * EmailReportFailedProcessor - Handle email delivery failure
 * 
 * Transition: sending → failed
 * Purpose: Handle email delivery failure
 */
@Component
public class EmailReportFailedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportFailedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailReportFailedProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailReport failure for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email report entity")
                .map(this::processEmailFailure)
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
     * Main business logic for email failure handling
     */
    private EntityWithMetadata<EmailReport> processEmailFailure(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailReport> context) {

        EntityWithMetadata<EmailReport> entityWithMetadata = context.entityResponse();
        EmailReport report = entityWithMetadata.entity();

        logger.debug("Processing email failure for report: {}", report.getReportId());

        // Mark email as failed
        report.setDeliveryStatus("FAILED");
        
        // Increment retry count
        Integer currentRetryCount = report.getRetryCount();
        if (currentRetryCount == null) {
            currentRetryCount = 0;
        }
        report.setRetryCount(currentRetryCount + 1);

        // Log the failure details
        String errorMessage = report.getErrorMessage();
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = "Unknown email delivery failure";
            report.setErrorMessage(errorMessage);
        }

        logger.error("Email delivery failed for report: {}, Error: {}, Retry count: {}", 
                    report.getReportId(), errorMessage, report.getRetryCount());

        return entityWithMetadata;
    }
}
