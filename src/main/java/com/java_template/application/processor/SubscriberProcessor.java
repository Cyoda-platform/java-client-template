package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
public class SubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\s]+@[^@\s]+\\.[^@\s]+$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(http|https)://.*$");

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        Subscriber subscriber = context.getEntity(Subscriber.class);
        if (subscriber == null) {
            logger.error("Subscriber entity is null");
            return EntityProcessorCalculationResponse.failure("Subscriber entity is null");
        }

        // Validate contact details
        if (!validateSubscriber(subscriber)) {
            logger.error("Subscriber validation failed for id: {}", subscriber.getSubscriberId());
            return EntityProcessorCalculationResponse.failure("Subscriber validation failed");
        }

        // Mark as active
        subscriber.setActive(true);

        // Normally persist or update subscriber state here
        logger.info("Subscriber processed and activated: id {}", subscriber.getSubscriberId());

        return EntityProcessorCalculationResponse.success();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean validateSubscriber(Subscriber subscriber) {
        if (subscriber.getSubscriberId() == null || subscriber.getSubscriberId().isBlank()) return false;
        if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) return false;
        if (subscriber.getContactDetail() == null || subscriber.getContactDetail().isBlank()) return false;

        String contactType = subscriber.getContactType().toLowerCase();
        String contactDetail = subscriber.getContactDetail();

        switch (contactType) {
            case "email":
                return EMAIL_PATTERN.matcher(contactDetail).matches();
            case "webhook":
                return URL_PATTERN.matcher(contactDetail).matches();
            default:
                logger.warn("Unknown contact type: {}", contactType);
                return false;
        }
    }
}
