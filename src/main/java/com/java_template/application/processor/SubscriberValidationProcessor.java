package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.subscriber.version_1.Subscriber.Filters;
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

import java.net.URI;
import java.util.regex.Pattern;

@Component
public class SubscriberValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberValidationProcessor(SerializerFactory serializerFactory) {
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

        // Validate contactEndpoint format: accept basic email or http(s) URL.
        boolean contactValid = false;
        String endpoint = entity.getContactEndpoint();
        if (endpoint != null) {
            endpoint = endpoint.trim();
            // simple email pattern
            Pattern emailPattern = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
            if (emailPattern.matcher(endpoint).matches()) {
                contactValid = true;
                // normalize email to lower-case
                entity.setContactEndpoint(endpoint.toLowerCase());
            } else {
                // try parse as URI with http/https
                try {
                    URI uri = new URI(endpoint);
                    String scheme = uri.getScheme();
                    if (scheme != null) {
                        String s = scheme.toLowerCase();
                        if (s.equals("http") || s.equals("https")) {
                            contactValid = true;
                            // keep original endpoint (trimmed)
                            entity.setContactEndpoint(endpoint);
                        }
                    }
                } catch (Exception ignored) {
                    contactValid = false;
                }
            }
        }

        // If contact is invalid, mark validation failed and return.
        if (!contactValid) {
            entity.setStatus("VALIDATION_FAILED");
            return entity;
        }

        // Contact is valid -> normalize and set defaults where appropriate.

        // Set default format if not provided
        if (entity.getFormat() == null || entity.getFormat().isBlank()) {
            entity.setFormat("summary");
        } else {
            entity.setFormat(entity.getFormat().trim().toLowerCase());
        }

        // Normalize filters if present
        Filters filters = entity.getFilters();
        if (filters != null) {
            if (filters.getCategory() != null) {
                String cat = filters.getCategory().trim();
                if (!cat.isEmpty()) filters.setCategory(cat.toLowerCase());
                else filters.setCategory(null);
            }
            if (filters.getCountry() != null) {
                String country = filters.getCountry().trim();
                if (!country.isEmpty()) filters.setCountry(country.toUpperCase());
                else filters.setCountry(null);
            }
            // prizeYear is Integer - nothing to normalize
        }

        // Transition to ACTIVE if currently in initial registration states.
        String currentStatus = entity.getStatus();
        if (currentStatus == null || currentStatus.isBlank()) {
            entity.setStatus("ACTIVE");
        } else {
            String cs = currentStatus.trim();
            if (cs.equalsIgnoreCase("REGISTERED") || cs.equalsIgnoreCase("PENDING")) {
                entity.setStatus("ACTIVE");
            } else {
                // keep existing status but normalize to upper-case standard
                entity.setStatus(cs.toUpperCase());
            }
        }

        return entity;
    }
}