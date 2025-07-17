package com.java_template.application.processor;

import com.java_template.application.entity.Report;
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

import java.util.Objects;

@Component
public class ReportProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public ReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("ReportProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Report.class)
                .withErrorHandler(this::handleReportError)
                .validate(this::isValidReport, "Invalid report state")
                .map(this::processReportBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ReportProcessor".equals(modelSpec.operationName()) &&
               "report".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidReport(Report report) {
        return report != null && report.isValid();
    }

    private ErrorInfo handleReportError(Throwable throwable, Report report) {
        logger.error("Error processing Report: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("PROCESSING_ERROR", throwable.getMessage());
    }

    // Business logic placeholder based on prototype processReport function
    private Report processReportBusinessLogic(Report report) {
        // Example logic: update generatedAt timestamp if empty
        if (report.getGeneratedAt() == null || report.getGeneratedAt().isEmpty()) {
            report.setGeneratedAt(java.time.Instant.now().toString());
        }
        // Additional business logic can be added here
        return report;
    }
}
