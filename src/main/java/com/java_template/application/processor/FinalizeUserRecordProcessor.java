package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class FinalizeUserRecordProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeUserRecordProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FinalizeUserRecordProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User entity = context.entity();

        // Finalization rules:
        // - Ensure retrievedAt is set (use current timestamp if missing)
        // - Ensure source is set (default to "ReqRes" if missing)
        // - Normalize email to lower-case and trim
        // - Trim and lightly normalize names (trim whitespace and capitalize first letter)
        // Do NOT call external persistence APIs for this entity (Cyoda will persist automatically).

        // Set retrievedAt if missing
        if (entity.getRetrievedAt() == null || entity.getRetrievedAt().isBlank()) {
            entity.setRetrievedAt(Instant.now().toString());
        }

        // Set default source if missing
        if (entity.getSource() == null || entity.getSource().isBlank()) {
            entity.setSource("ReqRes");
        }

        // Normalize email
        if (entity.getEmail() != null) {
            String email = entity.getEmail().trim().toLowerCase();
            entity.setEmail(email);
        }

        // Normalize first name
        if (entity.getFirstName() != null) {
            String fn = entity.getFirstName().trim();
            if (!fn.isBlank()) {
                entity.setFirstName(capitalize(fn));
            } else {
                entity.setFirstName(fn);
            }
        }

        // Normalize last name
        if (entity.getLastName() != null) {
            String ln = entity.getLastName().trim();
            if (!ln.isBlank()) {
                entity.setLastName(capitalize(ln));
            } else {
                entity.setLastName(ln);
            }
        }

        // Avatar is optional; trim if present
        if (entity.getAvatar() != null) {
            entity.setAvatar(entity.getAvatar().trim());
        }

        logger.debug("Finalized User record id={} retrievedAt={} source={}", entity.getId(), entity.getRetrievedAt(), entity.getSource());
        return entity;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.length() == 1) return value.toUpperCase();
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}