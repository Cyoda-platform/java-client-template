package com.java_template.application.processor;

import com.java_template.application.entity.Booking;
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
public class BookingProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public BookingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("BookingProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Booking for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Booking.class)
                .withErrorHandler(this::handleBookingError)
                .validate(Booking::isValid, "Invalid booking entity state")
                // Additional transformations or business validations can be added here
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "BookingProcessor".equals(modelSpec.operationName()) &&
               "booking".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleBookingError(Throwable throwable, Booking booking) {
        logger.error("Error processing Booking entity: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("BookingProcessingError", throwable.getMessage());
    }
}
