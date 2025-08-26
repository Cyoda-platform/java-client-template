package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Component
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    // Simple RFC-like email regex for validation purposes
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        Pattern.CASE_INSENSITIVE
    );

    public ValidateUserProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isBasicValidUser, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Basic synchronous validations:
     * - entity non-null and structural validity (user.isValid())
     * - email format
     */
    private boolean isBasicValidUser(User user) {
        if (user == null) return false;
        if (!user.isValid()) return false;

        String email = user.getEmail();
        if (email == null || email.isBlank()) return false;
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            logger.warn("User validation failed due to invalid email format: {}", email);
            return false;
        }

        // Basic checks passed; deeper duplicate check is performed in processEntityLogic (may use IO)
        return true;
    }

    /**
     * Full business logic for user validation and defaulting:
     * - Ensure email uniqueness (case-insensitive)
     * - Set defaults: role = "regular" if not provided
     *
     * Note: This method may perform blocking calls to the EntityService to validate uniqueness.
     * It's acceptable here to join the CompletableFuture to keep validation deterministic within the processor.
     */
    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        if (user == null) return null;

        // Normalize and ensure defaults
        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("regular");
        }
        if (user.getSubscribed() == null) {
            user.setSubscribed(false);
        }

        // Duplicate email check using search condition (case-insensitive)
        try {
            SearchConditionRequest searchRequest = SearchConditionRequest.group(
                "AND",
                Condition.of("$.email", "IEQUALS", user.getEmail())
            );

            // Use the EntityService search operation. This returns a CompletableFuture of results.
            // The interface exposes getItemsByCondition which accepts the search request as an Object.
            CompletableFuture<ArrayNode> resultFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                searchRequest
            );

            ArrayNode results = resultFuture.join();
            if (results != null && results.size() > 0) {
                // If there is an existing user with same email, treat as validation failure.
                // We throw an exception so the serializer pipeline can surface it as a validation error.
                String msg = String.format("User with email '%s' already exists", user.getEmail());
                logger.warn(msg);
                throw new IllegalStateException(msg);
            }
        } catch (IllegalStateException e) {
            // rethrow validation errors
            throw e;
        } catch (Exception e) {
            // If search fails unexpectedly, log and fail the validation to be safe.
            logger.error("Failed to validate uniqueness of user email '{}': {}", user.getEmail(), e.getMessage(), e);
            throw new IllegalStateException("Failed to validate user uniqueness", e);
        }

        // All validations passed; return entity. Cyoda will persist the entity state automatically.
        return user;
    }
}