package com.java_template.application.processor;

import com.java_template.application.entity.Subscriber;
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

import java.util.regex.Pattern;

@Component
public class SubscriberProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public SubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("SubscriberProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Subscriber.class)
                .validate(this::isValidEmail)
                .map(this::processSubscriberLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SubscriberProcessor".equals(modelSpec.operationName()) &&
                "subscriber".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEmail(Subscriber subscriber) {
        String email = subscriber.getEmail();
        if (email == null || email.isBlank()) {
            logger.error("Email is null or blank");
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        boolean matches = Pattern.compile(emailRegex).matcher(email).matches();
        if (!matches) {
            logger.error("Invalid email format: {}", email);
        }
        return matches;
    }

    private Subscriber processSubscriberLogic(Subscriber subscriber) {
        logger.info("Processing Subscriber with email: {}", subscriber.getEmail());
        // No additional processing currently
        return subscriber;
    }
}
