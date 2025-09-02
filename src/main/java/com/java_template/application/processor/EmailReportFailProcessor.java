package com.java_template.application.processor;

import com.java_template.application.entity.emailreport.version_1.EmailReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class EmailReportFailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportFailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailReportFailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailReport failure for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailReport.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EmailReport entity) {
        return entity != null;
    }

    private EmailReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EmailReport> context) {
        EmailReport entity = context.entity();

        // Set emailStatus to "FAILED"
        entity.setEmailStatus("FAILED");
        
        // Set failedAt timestamp
        entity.setFailedAt(LocalDateTime.now());
        
        // Set failureReason from context (this would typically come from the error context)
        if (entity.getFailureReason() == null || entity.getFailureReason().trim().isEmpty()) {
            entity.setFailureReason("Email processing failed - see logs for details");
        }
        
        // Log email failure
        logger.error("EmailReport failed for reportId: {} and requestId: {} at {}. Reason: {}", 
                    entity.getReportId(), entity.getRequestId(), entity.getFailedAt(), entity.getFailureReason());

        return entity;
    }
}
