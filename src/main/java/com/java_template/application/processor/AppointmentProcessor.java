package com.java_template.application.processor;

import com.java_template.application.entity.Appointment;
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
public class AppointmentProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public AppointmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("AppointmentProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Appointment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Appointment.class)
                .withErrorHandler(this::handleAppointmentError)
                .validate(Appointment::isValid, "Invalid appointment state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AppointmentProcessor".equals(modelSpec.operationName()) &&
                "appointment".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleAppointmentError(Throwable throwable, Appointment appointment) {
        logger.error("Error processing Appointment entity", throwable);
        return new ErrorInfo("AppointmentProcessingError", throwable.getMessage());
    }
}
