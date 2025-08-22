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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class AddressVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddressVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Basic postal code patterns for a few countries; can be extended later.
    private static final Map<String, Pattern> POSTAL_PATTERNS = new HashMap<>();
    static {
        POSTAL_PATTERNS.put("US", Pattern.compile("^\\d{5}(-\\d{4})?$"));
        POSTAL_PATTERNS.put("CA", Pattern.compile("^[A-Za-z]\\d[A-Za-z] ?\\d[A-Za-z]\\d$"));
        POSTAL_PATTERNS.put("GB", Pattern.compile("^[A-Za-z0-9 \\-]{2,10}$")); // simplified
        POSTAL_PATTERNS.put("DE", Pattern.compile("^\\d{5}$"));
        POSTAL_PATTERNS.put("FR", Pattern.compile("^\\d{5}$"));
        // default will be a lenient check
    }

    public AddressVerificationProcessor(SerializerFactory serializerFactory) {
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
        try {
            // Normalize and trim text fields if present
            if (entity.getCountry() != null) {
                String country = entity.getCountry().trim();
                if (!country.isEmpty()) {
                    entity.setCountry(country.toUpperCase());
                } else {
                    entity.setCountry(null);
                }
            }

            if (entity.getLine1() != null) {
                entity.setLine1(entity.getLine1().trim());
            }
            if (entity.getLine2() != null) {
                entity.setLine2(entity.getLine2().trim());
            }
            if (entity.getCity() != null) {
                entity.setCity(entity.getCity().trim());
            }
            if (entity.getPostalCode() != null) {
                entity.setPostalCode(entity.getPostalCode().trim());
            }

            // Ensure isDefault is not null (default false)
            if (entity.getIsDefault() == null) {
                entity.setIsDefault(Boolean.FALSE);
            }

            // Minimal presence checks (do not override entity.isValid behavior)
            boolean hasRequired = entity.getLine1() != null && !entity.getLine1().isBlank()
                && entity.getCity() != null && !entity.getCity().isBlank()
                && entity.getPostalCode() != null && !entity.getPostalCode().isBlank()
                && entity.getCountry() != null && !entity.getCountry().isBlank();

            if (!hasRequired) {
                logger.debug("Address {} failed basic non-blank checks.", entity.getId());
                entity.setVerified(Boolean.FALSE);
                entity.setStatus("Unverified");
                return entity;
            }

            // Validate postal code by country where pattern is available
            String ctry = entity.getCountry() != null ? entity.getCountry().trim().toUpperCase() : "";
            String postal = entity.getPostalCode() != null ? entity.getPostalCode().trim() : "";

            boolean postalValid = true;
            if (!ctry.isEmpty()) {
                Pattern p = POSTAL_PATTERNS.get(ctry);
                if (p != null) {
                    postalValid = p.matcher(postal).matches();
                } else {
                    // Fallback: require postal length between 2 and 12 and at least one alphanumeric
                    postalValid = postal.length() >= 2 && postal.length() <= 12 && postal.matches(".*[A-Za-z0-9].*");
                }
            } else {
                postalValid = postal.length() >= 2 && postal.length() <= 12;
            }

            if (!postalValid) {
                logger.debug("Address {} postal code '{}' not valid for country '{}'.", entity.getId(), postal, ctry);
                entity.setVerified(Boolean.FALSE);
                entity.setStatus("Unverified");
                return entity;
            }

            // If reached here, mark as verified.
            entity.setVerified(Boolean.TRUE);
            entity.setStatus("Verified");
            logger.info("Address {} marked as Verified.", entity.getId());
            return entity;

        } catch (Exception ex) {
            // On unexpected errors, mark unverified but do not throw to avoid breaking workflow.
            logger.error("Unexpected error in AddressVerificationProcessor for address {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            if (entity != null) {
                entity.setVerified(Boolean.FALSE);
                entity.setStatus("Unverified");
            }
            return entity;
        }
    }
}