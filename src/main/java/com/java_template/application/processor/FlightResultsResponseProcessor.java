package com.java_template.application.processor;

import com.java_template.application.entity.FlightResultsResponse;
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
public class FlightResultsResponseProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public FlightResultsResponseProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("FlightResultsResponseProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FlightResultsResponse for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(FlightResultsResponse.class)
                .withErrorHandler(this::handleFlightResultsResponseError)
                .validate(FlightResultsResponse::isValid, "Invalid FlightResultsResponse state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FlightResultsResponseProcessor".equals(modelSpec.operationName()) &&
                "flightResultsResponse".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleFlightResultsResponseError(Throwable throwable, FlightResultsResponse entity) {
        logger.error("Error processing FlightResultsResponse", throwable);
        return new ErrorInfo("FlightResultsResponseError", throwable.getMessage());
    }
}
