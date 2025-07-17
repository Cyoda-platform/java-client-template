package com.java_template.application.processor;

import com.java_template.application.entity.ReportMetadata;
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
public class ReportMetadataProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public ReportMetadataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("ReportMetadataProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportMetadata for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(ReportMetadata.class)
                .withErrorHandler(this::handleReportMetadataError)
                .validate(ReportMetadata::isValid, "Invalid ReportMetadata entity state")
                // No transformations or additional validation logic given in prototype
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ReportMetadataProcessor".equals(modelSpec.operationName()) &&
               "reportMetadata".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleReportMetadataError(Throwable throwable, ReportMetadata entity) {
        logger.error("Error processing ReportMetadata entity", throwable);
        return new ErrorInfo("ReportMetadataError", throwable.getMessage());
    }
}
