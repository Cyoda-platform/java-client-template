package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.report.version_1.Report;
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

@Component
public class SendReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SendReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report entity) {
        return entity != null && entity.getRecipients() != null && entity.getRecipients().size() > 0;
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report report = context.entity();

        try {
            // Simulate sending: check recipients and set deliveryStatus
            boolean anyFailed = false;
            for (String r : report.getRecipients()) {
                if (r == null || !r.contains("@")) {
                    anyFailed = true;
                    logger.warn("Invalid recipient {} for report {}", r, report.getTechnicalId());
                } else {
                    logger.info("Sending report {} to {}", report.getTechnicalId(), r);
                }
            }
            if (anyFailed) {
                report.setDeliveryStatus("FAILED");
                report.setFailureReason("one or more recipients invalid");
            } else {
                report.setDeliveryStatus("SENT");
                report.setAttempts(report.getAttempts() == null ? 1 : report.getAttempts() + 1);
            }
        } catch (Exception ex) {
            logger.error("Error sending report", ex);
            report.setDeliveryStatus("FAILED");
            report.setFailureReason("send error: " + ex.getMessage());
        }

        return report;
    }
}
