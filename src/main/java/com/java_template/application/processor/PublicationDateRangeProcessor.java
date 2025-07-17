package com.java_template.application.processor;

import com.java_template.application.entity.PublicationDateRange;
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
public class PublicationDateRangeProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PublicationDateRangeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PublicationDateRangeProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PublicationDateRange for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PublicationDateRange.class)
                .withErrorHandler(this::handlePublicationDateRangeError)
                .validate(PublicationDateRange::isValid, "Invalid PublicationDateRange entity state")
                .complete();
    }

    private ErrorInfo handlePublicationDateRangeError(Throwable t, PublicationDateRange publicationDateRange) {
        logger.error("Error processing PublicationDateRange entity", t);
        return new ErrorInfo("PublicationDateRangeProcessingError", t.getMessage());
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PublicationDateRangeProcessor".equals(modelSpec.operationName()) &&
                "publicationDateRange".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
