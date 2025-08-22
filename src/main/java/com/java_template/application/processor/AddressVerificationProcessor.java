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

import java.util.regex.Pattern;

@Component
public class AddressVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddressVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AddressVerificationProcessor(SerializerFactory serializerFactory) {
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

        // Business logic: Validate address format and mark as verified/unverified.
        // Rule summary:
        // - Required fields (id, userId, line1, city, country, postalCode) are already validated by isValidEntity.
        // - Perform lightweight format checks:
        //   * postalCode format validated according to country when possible (US, CA). Otherwise basic length check.
        //   * country should be an uppercase 2-letter code (if not, accept but normalize to upper-case).
        // - If all checks pass -> set verified = true and status = "Verified"
        // - Otherwise -> set verified = false and status = "Unverified"
        // Note: Do not perform external calls here. Keep idempotent and deterministic.

        boolean formatOk = true;

        // Normalize country code if present
        String country = entity.getCountry();
        if (country != null) {
            country = country.trim();
            if (!country.isEmpty()) {
                entity.setCountry(country.toUpperCase());
            }
        }

        // Basic non-blank checks (guarding, although isValid already checked)
        if (isBlank(entity.getLine1()) || isBlank(entity.getCity()) || isBlank(entity.getPostalCode()) || isBlank(entity.getCountry())) {
            formatOk = false;
            logger.debug("Address {} failed basic non-blank checks.", entity.getId());
        } else {
            // Validate postal code by country when possible
            String postal = entity.getPostalCode().trim();
            String ctry = entity.getCountry() != null ? entity.getCountry().trim().toUpperCase() : "";

            if (!validatePostalCodeForCountry(postal, ctry)) {
                formatOk = false;
                logger.debug("Address {} postal code '{}' not valid for country '{}'.", entity.getId(), postal, ctry);
            }
        }

        if (formatOk) {
            entity.setVerified(Boolean.TRUE);
            entity.setStatus("Verified");
            logger.info("Address {} marked as Verified.", entity.getId());
        } else {
            entity.setVerified(Boolean.FALSE);
            entity.setStatus("Unverified");
            logger.info("Address {} marked as Unverified.", entity.getId());
        }

        return entity;
    }

    // Helper methods

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean validatePostalCodeForCountry(String postal, String country) {
        if (postal == null || postal.isBlank()) return false;

        // US: 5 digits or 5-4
        if ("US".equalsIgnoreCase(country) || "USA".equalsIgnoreCase(country)) {
            Pattern us = Pattern.compile("^\\d{5}(-\\d{4})?$");
            return us.matcher(postal).matches();
        }

        // Canada: A1A 1A1 (allow with or without space)
        if ("CA".equalsIgnoreCase(country) || "CAN".equalsIgnoreCase(country)) {
            Pattern ca = Pattern.compile("^[A-Za-z]\\d[A-Za-z][ -]?\\d[A-Za-z]\\d$");
            return ca.matcher(postal).matches();
        }

        // UK: simplified broad check (alphanumeric between 5 and 8 chars)
        if ("GB".equalsIgnoreCase(country) || "UK".equalsIgnoreCase(country) || "GBR".equalsIgnoreCase(country)) {
            Pattern uk = Pattern.compile("^[A-Za-z0-9 ]{5,8}$");
            return uk.matcher(postal).matches();
        }

        // For other countries, accept postal codes with length between 3 and 10 and alphanumeric
        Pattern generic = Pattern.compile("^[A-Za-z0-9 \\-]{3,10}$");
        return generic.matcher(postal).matches();
    }
}