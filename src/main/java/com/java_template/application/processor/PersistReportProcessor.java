package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.report.version_1.Report;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PersistReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("PersistReportProcessor invoked for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .validate(this::isValidEntity, "Invalid report for persistence")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report report) {
        return report != null && report.getJobRef() != null && !report.getJobRef().isEmpty();
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report report = context.entity();
        try {
            if (report.getReportId() == null || report.getReportId().isEmpty()) {
                report.setReportId("report_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8));
            }
            if (report.getGeneratedAt() == null || report.getGeneratedAt().isEmpty()) {
                report.setGeneratedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            // Persist using entity service
            ObjectNode node = entityService.objectMapper().convertValue(report, ObjectNode.class);
            CompletableFuture<UUID> future = entityService.addItem(
                Report.ENTITY_NAME,
                String.valueOf(Report.ENTITY_VERSION),
                node
            );
            UUID persisted = future.get();
            logger.info("Report persisted with uuid={}", persisted);
        } catch (Exception e) {
            logger.error("Failed to persist report: {}", e.getMessage());
        }
        return report;
    }
}
