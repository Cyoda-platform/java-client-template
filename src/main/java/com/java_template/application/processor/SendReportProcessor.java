package com.java_template.application.processor;

import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class SendReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SendReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Sending report for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(WeeklyReport.class)
            .validate(this::isValidEntity, "Invalid WeeklyReport state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyReport entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyReport> context) {
        WeeklyReport report = context.entity();
        try {
            // In prototype, we simulate email sending and set status accordingly
            if (report.getAttachment_url() == null || report.getAttachment_url().isBlank()) {
                logger.warn("Report {} has no attachment_url - cannot send", report.getReport_id());
                report.setStatus("FAILED");
                return report;
            }

            // Simulate successful send
            report.setStatus("SENT");

            // Persist status update as a new entity entry
            CompletableFuture<UUID> idFuture = entityService.addItem(
                WeeklyReport.ENTITY_NAME, String.valueOf(WeeklyReport.ENTITY_VERSION), report
            );
            idFuture.get();
            logger.info("Report {} marked as SENT", report.getReport_id());
        } catch (Exception ex) {
            logger.error("Error sending report {}: {}", report.getReport_id(), ex.getMessage(), ex);
            report.setStatus("FAILED");
        }
        return report;
    }
}
