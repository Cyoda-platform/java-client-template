package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
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
        User user = context.entity();

        // 1. Validate email format
        String email = user.getEmail();
        if (email == null || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            logger.warn("Invalid email format for userId={} email={}", user.getUserId(), email);
            throw new IllegalArgumentException("Invalid email format");
        }

        // 2. Ensure email uniqueness (case-insensitive)
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.email", "IEQUALS", email)
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode found = itemsFuture.join();
            String currentTechnicalId = context.request().getEntityId();

            if (found != null) {
                for (JsonNode node : found) {
                    String techId = null;
                    if (node.has("technicalId") && !node.get("technicalId").isNull()) {
                        techId = node.get("technicalId").asText();
                    } else if (node.has("technicalID") && !node.get("technicalID").isNull()) {
                        techId = node.get("technicalID").asText();
                    }
                    // If there is any other record with same email and different technical id -> duplicate
                    if (techId == null || !techId.equals(currentTechnicalId)) {
                        logger.warn("Duplicate email detected for email={} existingTechnicalId={} currentTechnicalId={}",
                                email, techId, currentTechnicalId);
                        throw new IllegalStateException("Email already in use");
                    }
                }
            }
        } catch (RuntimeException ex) {
            // rethrow to signal processing failure
            throw ex;
        }

        // 3. Mark validation by updating updatedAt timestamp (entity will be persisted by Cyoda)
        user.setUpdatedAt(Instant.now().toString());

        return user;
    }
}