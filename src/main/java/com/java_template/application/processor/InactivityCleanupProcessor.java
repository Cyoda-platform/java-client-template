package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class InactivityCleanupProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InactivityCleanupProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InactivityCleanupProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InactivityCleanup for request: {}", request.getId());

        // For prototype: find users with status LOGGED_IN and mark them ANONYMOUS if inactivity threshold met
        try {
            CompletableFuture<ArrayNode> usersFuture = entityService.getItemsByCondition(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), SearchConditionRequest.group("AND", Condition.of("$.status", "EQUALS", "LOGGED_IN")), true);
            ArrayNode users = usersFuture.get();
            if (users != null) {
                for (int i = 0; i < users.size(); i++) {
                    ObjectNode node = (ObjectNode) users.get(i);
                    User user = objectMapper.convertValue(node, User.class);
                    // Simple policy: mark as ANONYMOUS (this is illustrative; real policy should check timestamps)
                    user.setStatus("ANONYMOUS");
                    if (node.has("technicalId")) {
                        try {
                            entityService.updateItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), java.util.UUID.fromString(node.get("technicalId").asText()), user).get();
                        } catch (Exception ex) {
                            logger.warn("Failed to update user during inactivity cleanup: {}", user.getUserId(), ex);
                        }
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error during inactivity cleanup", e);
        }

        return serializer.withRequest(request).toEntity(User.class).map(ctx -> ctx.entity()).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}
