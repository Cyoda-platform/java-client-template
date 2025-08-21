package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.userrecord.version_1.UserRecord;
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

import java.time.Instant;

@Component
public class TransformUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransformUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing TransformUserProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(UserRecord.class)
            .validate(this::isValidEntity, "Invalid user record")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(UserRecord user) {
        return user != null && user.isValid();
    }

    private UserRecord processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<UserRecord> context) {
        UserRecord user = context.entity();
        try {
            // Parse sourcePayload and extract fields if present
            if (user.getSourcePayload() != null && !user.getSourcePayload().isBlank()) {
                JsonNode n = objectMapper.readTree(user.getSourcePayload());
                // Fakerest user model fields: id, userName, password, firstName, lastName, email
                if (n.has("firstName")) user.setFirstName(n.get("firstName").asText(null));
                if (n.has("lastName")) user.setLastName(n.get("lastName").asText(null));
                if (n.has("email")) user.setEmail(n.get("email").asText(null));
                if (n.has("id")) user.setExternalId(n.get("id").isInt() ? n.get("id").asInt() : null);
            }

            // Normalize names: simple capitalization
            if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
                user.setFirstName(capitalize(user.getFirstName()));
            }
            if (user.getLastName() != null && !user.getLastName().isBlank()) {
                user.setLastName(capitalize(user.getLastName()));
            }

            // Canonicalize email: lowercase
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                user.setEmail(user.getEmail().trim().toLowerCase());
            }

            user.setTransformedAt(Instant.now().toString());
            user.setNormalized(true);
            user.setStatus("TRANSFORMED");
            logger.info("Transformed UserRecord externalId={}", user.getExternalId());
            return user;
        } catch (Exception ex) {
            logger.error("Error during TransformUserProcessor", ex);
            user.setStatus("ERROR");
            user.setErrorMessage("Transformation failed: " + ex.getMessage());
            return user;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        s = s.trim();
        if (s.length() == 1) return s.toUpperCase();
        return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
