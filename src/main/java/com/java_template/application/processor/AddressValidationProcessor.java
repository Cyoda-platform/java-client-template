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

        return serializer.withRequest(request)
            .toEntity(Address.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        if (entity == null) return null;

        try {
            // Normalize/trims
            if (entity.getLine1() != null) entity.setLine1(entity.getLine1().trim());
            if (entity.getLine2() != null) entity.setLine2(entity.getLine2().trim());
            if (entity.getCity() != null) entity.setCity(entity.getCity().trim());
            if (entity.getRegion() != null) entity.setRegion(entity.getRegion().trim());
            if (entity.getCountry() != null) entity.setCountry(entity.getCountry().trim().toUpperCase());
            if (entity.getPostalCode() != null) entity.setPostalCode(entity.getPostalCode().trim());
            if (entity.getPhone() != null) entity.setPhone(entity.getPhone().trim());

            // Postal code validation/basic normalization
            String country = entity.getCountry();
            String postal = entity.getPostalCode();
            boolean postalValid = true;
            if (postal == null || postal.isBlank()) {
                postalValid = false;
            } else {
                postal = postal.trim();
                if (country != null) {
                    switch (country.toUpperCase()) {
                        case "US":
                        case "USA":
                            postalValid = postal.matches("^\\d{5}(-\\d{4})?$");
                            break;
                        case "CA":
                        case "CAN":
                            postalValid = postal.matches("^[A-Za-z]\\d[A-Za-z]\\s?\\d[A-Za-z]\\d$");
                            break;
                        default:
                            // Generic check: at least 3 chars (to catch obvious invalid)
                            postalValid = postal.length() >= 3;
                            break;
                    }
                } else {
                    postalValid = postal.length() >= 3;
                }
            }
            if (!postalValid) {
                logger.warn("Address postal code validation failed for address id={}. Clearing postalCode.", entity.getId());
                entity.setPostalCode(null);
            }

            // Phone normalization/validation: keep digits and leading +
            String phone = entity.getPhone();
            if (phone != null && !phone.isBlank()) {
                String normalized = phone.replaceAll("\\s+", " ").trim();
                // Replace international "00" prefix with +
                normalized = normalized.replaceFirst("^00", "+");
                // Remove all characters except digits and leading +
                normalized = normalized.replaceAll("(?!^)\\D", "");
                // If only non-digits remain or too short, clear
                String digitsOnly = normalized.replaceAll("\\D", "");
                if (digitsOnly.length() < 7) {
                    logger.warn("Address phone validation failed for address id={}. Clearing phone.", entity.getId());
                    entity.setPhone(null);
                } else {
                    entity.setPhone(normalized);
                }
            }

            // Capitalize city and region words
            if (entity.getCity() != null && !entity.getCity().isBlank()) {
                entity.setCity(capitalizeWords(entity.getCity()));
            }
            if (entity.getRegion() != null && !entity.getRegion().isBlank()) {
                entity.setRegion(capitalizeWords(entity.getRegion()));
            }

            logger.info("Address validation completed for address id={}. PostalValid={}, PhonePresent={}",
                    entity.getId(), postalValid, entity.getPhone() != null);
        } catch (Exception ex) {
            logger.error("Unexpected error in AddressValidationProcessor for address id=" + (entity != null ? entity.getId() : "null"), ex);
            // Don't throw; best-effort normalization. Return entity as-is so workflow can continue.
        }

        return entity;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isBlank()) return input;
        String[] parts = input.toLowerCase().split("\\s+");
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