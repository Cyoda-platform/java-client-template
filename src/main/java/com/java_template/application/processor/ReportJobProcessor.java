package com.java_template.application.processor;

import com.java_template.application.entity.ReportJob;
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

import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class ReportJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public ReportJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("ReportJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(ReportJob.class)
                .withErrorHandler(this::handleReportJobError)
                .validate(this::isValidReportJob, "Invalid ReportJob state")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ReportJobProcessor".equals(modelSpec.operationName()) &&
               "reportJob".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidReportJob(ReportJob reportJob) {
        return reportJob.isValid();
    }

    private ErrorInfo handleReportJobError(Throwable throwable, ReportJob reportJob) {
        logger.error("Error processing ReportJob: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("PROCESSING_ERROR", throwable.getMessage());
    }

    private ReportJob processBusinessLogic(ReportJob reportJob) {
        // Example business logic: Update status if null or empty
        if (reportJob.getStatus() == null || reportJob.getStatus().isBlank()) {
            reportJob.setStatus("pending");
        }
        // Other business logic can be implemented here
        return reportJob;
    }
}
