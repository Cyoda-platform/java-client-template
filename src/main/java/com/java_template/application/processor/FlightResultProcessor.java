package com.java_template.application.processor;

import com.java_template.application.entity.FlightResult;
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
public class FlightResultProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public FlightResultProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("FlightResultProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FlightResult for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(FlightResult.class)
                .withErrorHandler(this::handleFlightResultError)
                .validate(FlightResult::isValid, "Invalid FlightResult state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FlightResultProcessor".equals(modelSpec.operationName()) &&
                "flightResult".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleFlightResultError(Throwable throwable, FlightResult entity) {
        logger.error("Error processing FlightResult", throwable);
        return new ErrorInfo("FlightResultError", throwable.getMessage());
    }
}
