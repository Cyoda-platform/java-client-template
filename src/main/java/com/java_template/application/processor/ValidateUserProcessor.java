package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final Pattern emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public ValidateUserProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid user state")
            .map(this::processEntityLogic)
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
        try {
            // Normalize email
            if (user.getEmail() != null) {
                user.setEmail(user.getEmail().trim().toLowerCase());
            }
            // Validate email format
            if (user.getEmail() == null || !emailPattern.matcher(user.getEmail()).matches()) {
                logger.warn("User {} has invalid email {}", user.getUserId(), user.getEmail());
                user.setStatus("PendingValidation");
                return user;
            }

            // Duplicate detection by email
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", user.getEmail())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode results = itemsFuture.get(5, TimeUnit.SECONDS);
            if (results != null && results.size() > 0) {
                ObjectNode existing = (ObjectNode) results.get(0);
                String existingTechId = existing.has("technicalId") ? existing.get("technicalId").asText() : null;
                String currentTechId = context.request().getEntityId();
                if (existingTechId != null && !existingTechId.equals(currentTechId)) {
                    logger.warn("Duplicate user found for email {} (existingTechId={})", user.getEmail(), existingTechId);
                    user.setStatus("NeedsReview");
                    return user;
                }
            }

            // All good
            user.setStatus("Active");
            logger.info("User {} activated", user.getUserId());
            return user;
        } catch (Exception e) {
            logger.error("Error validating user {}: {}", user.getUserId(), e.getMessage(), e);
            user.setStatus("PendingValidation");
            return user;
        }
    }
}
