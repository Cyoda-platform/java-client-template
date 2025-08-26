package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
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
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Component
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ValidateUserProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        if (entity == null) return null;

        try {
            // Basic validation: required fields (username, email) and email format
            boolean missingRequired = (entity.getUsername() == null || entity.getUsername().isBlank())
                    || (entity.getEmail() == null || entity.getEmail().isBlank());

            Pattern emailPattern = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
            boolean invalidEmail = entity.getEmail() == null || !emailPattern.matcher(entity.getEmail()).matches();

            boolean isDuplicate = false;

            // Duplicate check: find other users with same email or username
            try {
                SearchConditionRequest condition = SearchConditionRequest.group(
                    "OR",
                    Condition.of("$.email", "EQUALS", entity.getEmail() == null ? "" : entity.getEmail()),
                    Condition.of("$.username", "EQUALS", entity.getUsername() == null ? "" : entity.getUsername())
                );

                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    condition,
                    true
                );

                ArrayNode items = itemsFuture.join();
                if (items != null) {
                    for (JsonNode node : items) {
                        // Try to exclude the same source id (if present)
                        Integer existingSourceId = null;
                        if (node.has("id") && !node.get("id").isNull()) {
                            try {
                                existingSourceId = node.get("id").asInt();
                            } catch (Exception ignored) {}
                        }
                        if (existingSourceId != null && entity.getId() != null) {
                            if (!existingSourceId.equals(entity.getId())) {
                                isDuplicate = true;
                                break;
                            } else {
                                // same source id -> not a duplicate
                                continue;
                            }
                        } else {
                            // If we cannot compare ids, any match is treated as duplicate
                            isDuplicate = true;
                            break;
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to evaluate duplicates for user (email/username): {}, continuing without duplicate flag. Error: {}", entity.getEmail(), ex.getMessage());
            }

            // Decision logic based on validations
            if (missingRequired || invalidEmail) {
                entity.setValidationStatus("INVALID");
            } else if (isDuplicate) {
                entity.setValidationStatus("INVALID");
            } else {
                entity.setValidationStatus("VALID");
            }

        } catch (Exception ex) {
            logger.error("Error while validating user: {}", ex.getMessage(), ex);
            // On unexpected errors, mark as INVALID to avoid promoting bad data
            entity.setValidationStatus("INVALID");
        }

        return entity;
    }
}