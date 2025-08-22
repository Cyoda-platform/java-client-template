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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ValidateSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // simple RFC-like email check (sufficient for basic validation)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    public ValidateSubscriberProcessor(SerializerFactory serializerFactory) {
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
        Subscriber subscriber = context.entity();

        if (subscriber == null) {
            logger.warn("Subscriber entity is null in execution context");
            return subscriber;
        }

        // Normalize some fields
        if (subscriber.getContactMethod() != null) {
            subscriber.setContactMethod(subscriber.getContactMethod().trim().toLowerCase(Locale.ROOT));
        }
        if (subscriber.getContactDetails() != null) {
            subscriber.setContactDetails(subscriber.getContactDetails().trim());
        }
        if (subscriber.getPreference() != null) {
            subscriber.setPreference(subscriber.getPreference().trim().toLowerCase(Locale.ROOT));
        }

        boolean contactValid = validateContact(subscriber.getContactMethod(), subscriber.getContactDetails());
        boolean preferenceValid = validatePreference(subscriber.getPreference());

        if (!contactValid) {
            subscriber.setStatus("FAILED");
            logger.info("Subscriber validation failed due to invalid contact details. subscriberId={}, contactMethod={}, contactDetails={}",
                subscriber.getId(), subscriber.getContactMethod(), subscriber.getContactDetails());
            return subscriber;
        }

        if (!preferenceValid) {
            // If preference invalid, set to a safe default rather than failing the subscriber
            logger.warn("Subscriber preference invalid or unknown, defaulting to 'immediate'. subscriberId={}, preference={}",
                subscriber.getId(), subscriber.getPreference());
            subscriber.setPreference("immediate");
        }

        // If everything ok mark as ACTIVE (per functional requirement)
        subscriber.setStatus("ACTIVE");
        logger.info("Subscriber validated and activated. subscriberId={}", subscriber.getId());

        return subscriber;
    }

    private boolean validateContact(String method, String details) {
        if (method == null || details == null) return false;

        switch (method.toLowerCase(Locale.ROOT)) {
            case "email":
                return isValidEmail(details);
            case "webhook":
            case "url":
            case "webhook_url":
                return isValidUrl(details);
            default:
                // unknown contact method -> consider invalid
                logger.warn("Unknown contact method encountered during validation: {}", method);
                return false;
        }
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private boolean isValidUrl(String url) {
        if (url == null) return false;
        try {
            URL u = new URL(url);
            // basic checks: protocol must be http or https and host must be present
            String protocol = u.getProtocol();
            String host = u.getHost();
            return (protocol != null && (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")))
                && host != null && !host.isBlank();
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private boolean validatePreference(String preference) {
        if (preference == null || preference.isBlank()) return true; // preference optional; leave to defaulting logic
        String p = preference.toLowerCase(Locale.ROOT);
        return p.equals("immediate") || p.equals("dailydigest") || p.equals("weeklydigest") || p.equals("daily") || p.equals("weekly");
    }
}