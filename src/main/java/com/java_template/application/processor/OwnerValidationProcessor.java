package com.java_template.application.processor;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@Component
public class OwnerValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OwnerValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.isValid();
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner entity = context.entity();

        // Business rules:
        // - Validate and normalize contactEmail (lowercase, trimmed) using a simple regex.
        // - Validate and normalize contactPhone (remove separators) using a simple regex.
        // - Determine profile completeness: address present AND at least one valid contact (email or phone).
        // - Encode derived state into the existing 'preferences' field since Owner has no dedicated status field.
        //   We will add tags like "STATUS:ACTIVE" or "STATUS:SUSPENDED" and "PROFILE:COMPLETE" to preferences,
        //   preserving unrelated existing preferences content.

        // Email validation regex (simple)
        Pattern emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
        // Phone validation regex: optional leading + and 7-15 digits
        Pattern phonePattern = Pattern.compile("^\\+?\\d{7,15}$");

        String contactEmail = entity.getContactEmail();
        String contactPhone = entity.getContactPhone();

        boolean emailValid = false;
        boolean phoneValid = false;

        // Normalize and validate email
        if (contactEmail != null && !contactEmail.isBlank()) {
            String normalizedEmail = contactEmail.trim().toLowerCase();
            Matcher m = emailPattern.matcher(normalizedEmail);
            if (m.matches()) {
                emailValid = true;
                entity.setContactEmail(normalizedEmail);
            } else {
                // leave as-is (could be useful to keep original), but mark invalid
                emailValid = false;
            }
        }

        // Normalize and validate phone
        if (contactPhone != null && !contactPhone.isBlank()) {
            // remove common separators but keep leading +
            String normalizedPhone = contactPhone.trim().replaceAll("[\\s()\\-\\.]", "");
            // ensure plus is only at start
            if (normalizedPhone.indexOf('+') > 0) {
                // invalid format (plus not at start) - leave as-is and consider invalid
                phoneValid = false;
            } else {
                Matcher pm = phonePattern.matcher(normalizedPhone);
                if (pm.matches()) {
                    phoneValid = true;
                    entity.setContactPhone(normalizedPhone);
                } else {
                    phoneValid = false;
                }
            }
        }

        boolean hasAddress = entity.getAddress() != null && !entity.getAddress().isBlank();
        boolean profileComplete = hasAddress && (emailValid || phoneValid);

        // Build updated preferences by preserving existing non-status/profile tokens
        String existingPrefs = entity.getPreferences();
        List<String> preserved = new ArrayList<>();
        if (existingPrefs != null && !existingPrefs.isBlank()) {
            String[] parts = existingPrefs.split(";");
            for (String p : parts) {
                String t = p.trim();
                if (t.isEmpty()) continue;
                // filter out prior STATUS: or PROFILE: tags to avoid duplicates
                if (t.startsWith("STATUS:") || t.startsWith("PROFILE:")) continue;
                preserved.add(t);
            }
        }

        StringJoiner sj = new StringJoiner(";");
        // add preserved items first
        for (String p : preserved) {
            sj.add(p);
        }

        // Add profile and status tags
        if (profileComplete) {
            sj.add("PROFILE:COMPLETE");
        } else {
            sj.add("PROFILE:INCOMPLETE");
        }

        if (profileComplete && (emailValid || phoneValid)) {
            sj.add("STATUS:ACTIVE");
        } else {
            sj.add("STATUS:SUSPENDED");
        }

        entity.setPreferences(sj.toString());

        // Return modified entity - Cyoda will persist changes automatically
        return entity;
    }
}