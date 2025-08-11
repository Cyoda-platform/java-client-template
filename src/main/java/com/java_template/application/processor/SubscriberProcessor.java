package com.java_template.application.processor;

import com.java_template.application.entity.Subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

@Component
public class SubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber subscriber) {
        if (subscriber == null) return false;
        if (subscriber.getContactType() == null || subscriber.getContactType().isEmpty()) return false;
        if (subscriber.getContactAddress() == null || subscriber.getContactAddress().isEmpty()) return false;
        // Validate contact type and address format
        String type = subscriber.getContactType().toLowerCase();
        if (type.equals("email")) {
            return subscriber.getContactAddress().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
        } else if (type.equals("webhook")) {
            try {
                new URL(subscriber.getContactAddress());
                return true;
            } catch (MalformedURLException e) {
                return false;
            }
        } else {
            // Unknown contact type
            return false;
        }
    }

    private Subscriber processEntityLogic(Subscriber subscriber) {
        // No further processing needed
        return subscriber;
    }
}