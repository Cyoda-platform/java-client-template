package com.java_template.application.processor;

import com.java_template.application.entity.PerformanceReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PerformanceReportProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PerformanceReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PerformanceReportProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PerformanceReport for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(PerformanceReport.class)
                .validate(PerformanceReport::isValid, "Invalid PerformanceReport entity state")
                .map(this::processPerformanceReportLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PerformanceReportProcessor".equals(modelSpec.operationName()) &&
               "performanceReport".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    /**
     * Actual business logic for processing PerformanceReport entity.
     * Logic derived from processPerformanceReport() flow in functional requirements section 3.
     */
    private PerformanceReport processPerformanceReportLogic(PerformanceReport entity) {
        // Validation: Confirm report integrity and format
        if (entity.getSummary() == null || entity.getSummary().isBlank()) {
            throw new IllegalStateException("PerformanceReport summary cannot be empty");
        }
        if (entity.getReportFileUrl() == null || entity.getReportFileUrl().isBlank()) {
            throw new IllegalStateException("PerformanceReport file URL cannot be empty");
        }
        if (entity.getGeneratedAt() == null) {
            throw new IllegalStateException("PerformanceReport generatedAt timestamp cannot be null");
        }

        // Persistence and Notification steps are handled outside the processor
        // Processor modifies the entity only if necessary before persisting, here we trust entity is valid.

        return entity;
    }
}