package com.java_template.application.processor;

import com.java_template.application.entity.SummaryReport;
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
public class SummaryReportProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public SummaryReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("SummaryReportProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SummaryReport for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(SummaryReport.class)
                .withErrorHandler(this::handleSummaryReportError)
                .validate(SummaryReport::isValid, "Invalid SummaryReport entity state")
                .complete();
    }

    private ErrorInfo handleSummaryReportError(Throwable t, SummaryReport summaryReport) {
        logger.error("Error processing SummaryReport entity", t);
        return new ErrorInfo("SummaryReportProcessingError", t.getMessage());
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SummaryReportProcessor".equals(modelSpec.operationName()) &&
                "summaryReport".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
