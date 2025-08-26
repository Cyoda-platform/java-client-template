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
public class RegisterSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RegisterSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RegisterSubscriberProcessor(SerializerFactory serializerFactory) {
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

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        // Business rule:
        // - Activate subscriber (set active = true) when email is valid and subscriber has not opted out (optOutAt == null)
        // - If invalid email or opted out -> ensure active = false
        // - Normalize email (trim)
        // - Initialize lastDeliveryStatus to "PENDING" when activating and if not already set

        if (entity == null) {
            logger.warn("Subscriber entity is null in execution context");
            return null;
        }

        String email = entity.getEmail();
        if (email != null) {
            email = email.trim();
            entity.setEmail(email);
        }

        boolean emailValid = false;
        if (email != null && !email.isBlank()) {
            // Simple RFC-lite email validation
            String emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
            emailValid = Pattern.matches(emailRegex, email);
        }

        boolean optedOut = false;
        if (entity.getOptOutAt() != null && !entity.getOptOutAt().isBlank()) {
            optedOut = true;
        }

        if (emailValid && !optedOut) {
            if (!Boolean.TRUE.equals(entity.getActive())) {
                logger.info("Activating subscriber id={}", entity.getId());
            }
            entity.setActive(true);
            if (entity.getLastDeliveryStatus() == null || entity.getLastDeliveryStatus().isBlank()) {
                entity.setLastDeliveryStatus("PENDING");
            }
        } else {
            if (!Boolean.FALSE.equals(entity.getActive())) {
                logger.info("Deactivating subscriber id={} (emailValid={}, optedOut={})", entity.getId(), emailValid, optedOut);
            }
            entity.setActive(false);
            // If opted out explicitly, keep lastDeliveryStatus as FAILED_DELIVERY marker
            if (optedOut && (entity.getLastDeliveryStatus() == null || entity.getLastDeliveryStatus().isBlank())) {
                entity.setLastDeliveryStatus("OPTED_OUT");
            }
        }

        return entity;
    }
}