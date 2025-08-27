package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SendEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendEmailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        return entity != null && entity.isValid();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob entity = context.entity();

        // Basic validations before sending email
        String recipients = entity.getRecipients();
        String generatedUrl = entity.getGeneratedUrl();

        if (recipients == null || recipients.isBlank()) {
            logger.error("ReportJob [{}] has no recipients. Marking as FAILED.", entity.getId());
            entity.setStatus("FAILED");
            return entity;
        }

        if (generatedUrl == null || generatedUrl.isBlank()) {
            logger.error("ReportJob [{}] has no generatedUrl to attach. Marking as FAILED.", entity.getId());
            entity.setStatus("FAILED");
            return entity;
        }

        // Validate recipients format (simple validation: at least one '@' per recipient)
        String[] parts = recipients.split(",");
        boolean allValid = true;
        for (String r : parts) {
            String trimmed = r.trim();
            if (trimmed.isBlank() || !trimmed.contains("@") || !trimmed.contains(".")) {
                logger.warn("ReportJob [{}] has invalid recipient '{}'", entity.getId(), trimmed);
                allValid = false;
                break;
            }
        }

        if (!allValid) {
            logger.error("ReportJob [{}] contains invalid recipient(s). Marking as FAILED.", entity.getId());
            entity.setStatus("FAILED");
            return entity;
        }

        // Simulate sending email: attach generatedUrl and set status to COMPLETED
        try {
            logger.info("Sending report [{}] to recipients: {} with attachment: {}", entity.getName(), recipients, generatedUrl);
            // NOTE: Actual email sending/integration must be implemented elsewhere.
            entity.setStatus("COMPLETED");
            logger.info("ReportJob [{}] email send simulated successfully. Status set to COMPLETED.", entity.getId());
        } catch (Exception ex) {
            logger.error("Failed to send report [{}]: {}", entity.getId(), ex.getMessage(), ex);
            entity.setStatus("FAILED");
        }

        return entity;
    }
}