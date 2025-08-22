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
import java.util.regex.Pattern;

@Component
public class ValidateSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateSubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Default to failed unless validation passes
        String newStatus = "FAILED";

        String contactDetails = entity.getContactDetails();
        String contactMethod = entity.getContactMethod();

        boolean valid = false;

        if (contactDetails != null) {
            contactDetails = contactDetails.trim();
        }

        if (contactMethod != null) {
            contactMethod = contactMethod.trim();
        }

        if (contactDetails == null || contactDetails.isBlank()) {
            logger.warn("Subscriber validation failed: empty contactDetails for subscriber id={}", entity.getId());
            valid = false;
        } else if (contactMethod == null || contactMethod.isBlank()) {
            logger.warn("Subscriber validation failed: empty contactMethod for subscriber id={}", entity.getId());
            valid = false;
        } else if ("email".equalsIgnoreCase(contactMethod)) {
            // Basic email pattern validation
            Pattern emailPattern = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
            valid = emailPattern.matcher(contactDetails).matches();
            if (!valid) {
                logger.warn("Subscriber validation failed: invalid email '{}' for subscriber id={}", contactDetails, entity.getId());
            }
        } else if ("webhook".equalsIgnoreCase(contactMethod) || "http".equalsIgnoreCase(contactMethod) || "https".equalsIgnoreCase(contactMethod)) {
            // Validate URL and require http/https scheme
            try {
                URL url = new URL(contactDetails);
                String protocol = url.getProtocol();
                if (protocol != null && (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
                    valid = true;
                } else {
                    valid = false;
                    logger.warn("Subscriber validation failed: webhook URL must use http/https '{}', subscriber id={}", contactDetails, entity.getId());
                }
            } catch (MalformedURLException e) {
                valid = false;
                logger.warn("Subscriber validation failed: malformed webhook URL '{}' for subscriber id={}", contactDetails, entity.getId());
            }
        } else {
            // Unknown contact method -> treat as invalid
            logger.warn("Subscriber validation failed: unsupported contactMethod '{}' for subscriber id={}", contactMethod, entity.getId());
            valid = false;
        }

        if (valid) {
            newStatus = "ACTIVE";
            logger.info("Subscriber validated and set to ACTIVE for id={}", entity.getId());
        } else {
            newStatus = "FAILED";
            logger.info("Subscriber validation resulted in FAILED for id={}", entity.getId());
        }

        entity.setStatus(newStatus);

        return entity;
    }
}