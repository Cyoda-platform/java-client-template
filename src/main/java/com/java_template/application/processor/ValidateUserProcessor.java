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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

        // Business logic:
        // 1. Validate email format.
        // 2. Check email uniqueness among User entities (case-insensitive).
        // 3. If email invalid or not unique -> mark user as inactive.
        // 4. If valid -> ensure a default role exists (Customer) and mark active.

        String email = entity.getEmail();
        boolean emailFormatValid = false;
        if (email != null) {
            // simple RFC-lite validation
            emailFormatValid = email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
        }

        if (!emailFormatValid) {
            logger.warn("User [{}] has invalid email format: {}", entity.getUserId(), email);
            entity.setStatus("inactive");
            return entity;
        }

        // Build search condition to find users with same email (case-insensitive)
        SearchConditionRequest condition = SearchConditionRequest.group(
            "AND",
            Condition.of("$.email", "IEQUALS", email)
        );

        try {
            ArrayNode results = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                condition,
                true
            ).join();

            boolean conflict = false;
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    ObjectNode node = (ObjectNode) results.get(i);
                    if (node == null) continue;
                    // Compare business userId to avoid matching itself
                    if (node.has("userId")) {
                        String foundUserId = node.get("userId").asText(null);
                        if (foundUserId == null) continue;
                        if (!foundUserId.equals(entity.getUserId())) {
                            conflict = true;
                            break;
                        }
                    } else {
                        // If stored record doesn't have userId field, conservatively treat as conflict
                        conflict = true;
                        break;
                    }
                }
            }

            if (conflict) {
                logger.warn("Email '{}' is already in use by another user. Marking user [{}] inactive.", email, entity.getUserId());
                entity.setStatus("inactive");
                return entity;
            }

        } catch (Exception ex) {
            logger.error("Error while checking email uniqueness for user {}: {}", entity.getUserId(), ex.getMessage(), ex);
            // On error be conservative: mark inactive to avoid enabling a potentially invalid user
            entity.setStatus("inactive");
            return entity;
        }

        // Email valid and unique -> ensure role and mark active
        if (entity.getRole() == null || entity.getRole().isBlank()) {
            entity.setRole("Customer");
        }
        entity.setStatus("active");
        logger.info("User [{}] validated successfully. role={}, status={}", entity.getUserId(), entity.getRole(), entity.getStatus());

        return entity;
    }
}