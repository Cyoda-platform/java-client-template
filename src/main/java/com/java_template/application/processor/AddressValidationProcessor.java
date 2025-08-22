package com.java_template.application.processor;

import com.java_template.application.entity.address.version_1.Address;
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

@Component
public class AddressValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddressValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AddressValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Address for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Address.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Address entity) {
        return entity != null && entity.isValid();
    }

    private Address processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Address> context) {
        Address entity = context.entity();

        // Basic normalization: trim strings and normalize country code
        if (entity.getLine1() != null) entity.setLine1(entity.getLine1().trim());
        if (entity.getLine2() != null) entity.setLine2(entity.getLine2().trim());
        if (entity.getCity() != null) entity.setCity(entity.getCity().trim());
        if (entity.getRegion() != null) entity.setRegion(entity.getRegion().trim());
        if (entity.getCountry() != null) entity.setCountry(entity.getCountry().trim().toUpperCase());
        if (entity.getPostalCode() != null) entity.setPostalCode(entity.getPostalCode().trim());
        if (entity.getPhone() != null) entity.setPhone(entity.getPhone().trim());

        boolean postalValid = true;
        String country = entity.getCountry();
        String postal = entity.getPostalCode();

        // Country-aware postal code validation (basic)
        if (postal == null || postal.isBlank()) {
            postalValid = false;
        } else if ("US".equalsIgnoreCase(country)) {
            // 5 or 5-4 format
            postalValid = postal.matches("^\\d{5}(-\\d{4})?$");
        } else if ("CA".equalsIgnoreCase(country)) {
            // Canadian postal code ANA NAN
            postalValid = postal.matches("^[A-Za-z]\\d[A-Za-z] ?\\d[A-Za-z]\\d$");
        } else {
            // Generic: alphanumeric, dashes, spaces, length 3-10
            postalValid = postal.matches("^[A-Za-z0-9 \\-]{3,10}$");
        }

        if (!postalValid) {
            logger.warn("Address postal code validation failed for address id={}. Marking address as invalid.", entity.getId());
            // Mark as invalid by clearing postalCode so entity.isValid() will fail and system can treat it as invalid.
            entity.setPostalCode(null);
        }

        // Basic phone validation: allow digits, spaces, +, -, parentheses; require at least 7 digits
        String phone = entity.getPhone();
        if (phone != null && !phone.isBlank()) {
            String digitsOnly = phone.replaceAll("[^0-9]", "");
            if (digitsOnly.length() < 7) {
                logger.warn("Address phone validation failed for address id={}. Clearing phone.", entity.getId());
                entity.setPhone(null);
            } else {
                // normalize spacing: collapse multiple spaces
                entity.setPhone(phone.replaceAll("\\s+", " "));
            }
        }

        // Final normalization for region and city capitalization (light touch)
        if (entity.getCity() != null && !entity.getCity().isBlank()) {
            entity.setCity(capitalizeWords(entity.getCity()));
        }
        if (entity.getRegion() != null && !entity.getRegion().isBlank()) {
            entity.setRegion(capitalizeWords(entity.getRegion()));
        }

        logger.info("Address validation completed for address id={}. PostalValid={}, PhonePresent={}", entity.getId(), postalValid, entity.getPhone() != null);

        return entity;
    }

    private String capitalizeWords(String input) {
        String[] parts = input.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() > 0) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1));
            }
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }
}