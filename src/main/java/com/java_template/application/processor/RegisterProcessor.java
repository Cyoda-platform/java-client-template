package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class RegisterProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RegisterProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public RegisterProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Register for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid user payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User user) {
        return user != null && user.getEmail() != null && !user.getEmail().isBlank();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        // Enforce unique email (simple check)
        try {
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> usersFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), SearchConditionRequest.group("AND", Condition.of("$.email", "EQUALS", user.getEmail())), true
            );
            com.fasterxml.jackson.databind.node.ArrayNode found = usersFuture.get();
            if (found != null && found.size() > 0) {
                throw new IllegalStateException("Email already registered: " + user.getEmail());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while checking unique email", e);
            throw new RuntimeException(e);
        }

        // Set status to REGISTERED
        user.setStatus("REGISTERED");
        logger.info("User {} registered", user.getUserId());
        return user;
    }
}
