package com.java_template.application.processor;

import com.java_template.application.entity.FlightSearchRequest;
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

@Component
public class FlightSearchRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public FlightSearchRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("FlightSearchRequestProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FlightSearchRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(FlightSearchRequest.class)
                .withErrorHandler(this::handleFlightSearchRequestError)
                .validate(FlightSearchRequest::isValid, "Invalid FlightSearchRequest state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FlightSearchRequestProcessor".equals(modelSpec.operationName()) &&
                "flightSearchRequest".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleFlightSearchRequestError(Throwable throwable, FlightSearchRequest entity) {
        logger.error("Error processing FlightSearchRequest", throwable);
        return new ErrorInfo("FlightSearchRequestError", throwable.getMessage());
    }
}
