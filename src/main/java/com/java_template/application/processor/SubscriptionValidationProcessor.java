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

import java.net.URI;
import java.util.regex.Pattern;

@Component
public class SubscriptionValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriptionValidationProcessor(SerializerFactory serializerFactory) {
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

        // Normalize basic fields
        if (entity.getSubscriberId() != null) {
            String sid = entity.getSubscriberId().trim();
            entity.setSubscriberId(sid);
        }

        if (entity.getContactType() != null) {
            String ct = entity.getContactType().trim().toUpperCase();
            entity.setContactType(ct);
        }

        if (entity.getContactAddress() != null) {
            String ca = entity.getContactAddress().trim();
            entity.setContactAddress(ca);
        }

        // Validate contact info based on contactType
        boolean contactValid = true;
        String contactType = entity.getContactType();
        String contactAddress = entity.getContactAddress();

        if (contactType != null) {
            switch (contactType) {
                case "EMAIL":
                    // Simple email validation
                    Pattern emailPattern = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
                    if (contactAddress == null || !emailPattern.matcher(contactAddress).matches()) {
                        contactValid = false;
                        logger.warn("Subscriber {} has invalid EMAIL address: {}", entity.getSubscriberId(), contactAddress);
                    }
                    break;
                case "WEBHOOK":
                    // Validate webhook URL (must be http or https and have a host)
                    if (contactAddress == null) {
                        contactValid = false;
                        logger.warn("Subscriber {} has null WEBHOOK address", entity.getSubscriberId());
                    } else {
                        try {
                            URI uri = new URI(contactAddress);
                            String scheme = uri.getScheme();
                            String host = uri.getHost();
                            if (scheme == null || host == null) {
                                contactValid = false;
                                logger.warn("Subscriber {} has invalid WEBHOOK URL (missing scheme/host): {}", entity.getSubscriberId(), contactAddress);
                            } else {
                                String s = scheme.toLowerCase();
                                if (!s.equals("http") && !s.equals("https")) {
                                    contactValid = false;
                                    logger.warn("Subscriber {} has unsupported WEBHOOK scheme: {}", entity.getSubscriberId(), scheme);
                                }
                            }
                        } catch (Exception e) {
                            contactValid = false;
                            logger.warn("Subscriber {} has malformed WEBHOOK URL: {}. Error: {}", entity.getSubscriberId(), contactAddress, e.getMessage());
                        }
                    }
                    break;
                default:
                    // For OTHER or unknown types, accept any non-blank address
                    if (contactAddress == null || contactAddress.isBlank()) {
                        contactValid = false;
                        logger.warn("Subscriber {} has empty contact address for type {}.", entity.getSubscriberId(), contactType);
                    }
                    break;
            }
        } else {
            // contactType missing - should have been caught by isValidEntity, but double-check
            contactValid = false;
            logger.warn("Subscriber {} missing contactType.", entity.getSubscriberId());
        }

        // If contact is invalid, deactivate the subscriber to prevent notifications
        if (!contactValid) {
            if (Boolean.TRUE.equals(entity.getActive())) {
                entity.setActive(false);
                logger.info("Deactivating subscriber {} due to invalid contact information.", entity.getSubscriberId());
            } else {
                logger.debug("Subscriber {} already inactive.", entity.getSubscriberId());
            }
        }

        return entity;
    }
}