package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class ValidateSubscriberContactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSubscriberContactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(http|https)://.*$");

    public ValidateSubscriberContactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating subscriber contact for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid Subscriber contact details")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber subscriber) {
        if (subscriber == null) {
            logger.error("Subscriber entity is null");
            return false;
        }

        if (subscriber.getContactType() == null || subscriber.getContactType().isEmpty()) {
            logger.error("ContactType is required");
            return false;
        }

        if (subscriber.getContactDetails() == null || subscriber.getContactDetails().isEmpty()) {
            logger.error("ContactDetails is required");
            return false;
        }

        String type = subscriber.getContactType().toLowerCase();
        if ("email" .equals(type)) {
            if (!EMAIL_PATTERN.matcher(subscriber.getContactDetails()).matches()) {
                logger.error("Invalid email format: {}", subscriber.getContactDetails());
                return false;
            }
        } else if ("webhook".equals(type)) {
            if (!URL_PATTERN.matcher(subscriber.getContactDetails()).matches()) {
                logger.error("Invalid webhook URL format: {}", subscriber.getContactDetails());
                return false;
            }
        } else {
            logger.error("Unsupported contact type: {}", subscriber.getContactType());
            return false;
        }

        return true;
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        // No additional processing
        return subscriber;
    }
}
