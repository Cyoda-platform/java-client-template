package com.java_template.application.processor;

import com.java_template.application.entity.InventoryReport;
import com.java_template.common.serializer.ErrorInfo;
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
public class InventoryReportProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public InventoryReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("InventoryReportProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventoryReport for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(InventoryReport.class)
                .withErrorHandler(this::handleInventoryReportError)
                .validate(InventoryReport::isValid, "Invalid InventoryReport state")
                .map(this::processMetrics)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "InventoryReportProcessor".equals(modelSpec.operationName()) &&
                "inventoryReport".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private InventoryReport processMetrics(InventoryReport report) {
        if (report.getMetrics() != null && report.getMetrics().isValid()) {
            logger.info("Metrics are valid for InventoryReport: {}", report.getReportId());
        } else {
            logger.warn("Metrics missing or invalid for InventoryReport: {}", report.getReportId());
        }
        return report;
    }

    private ErrorInfo handleInventoryReportError(Throwable throwable, InventoryReport report) {
        logger.error("Error processing InventoryReport: {}", report != null ? report.getReportId() : "null", throwable);
        return new ErrorInfo("InventoryReportProcessingError", throwable.getMessage());
    }
}
