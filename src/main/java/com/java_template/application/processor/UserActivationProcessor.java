package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class UserActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UserActivationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User activation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User user) {
        return user != null && user.isValid();
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<User> processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        List<String> errors = new ArrayList<>();

        try {
            // Check email uniqueness via datastore
            // This is a simple in-memory/invocation check using entityService.getItemsByCondition
            try {
                com.fasterxml.jackson.databind.node.ObjectNode condition = com.java_template.common.util.Json.mapper().createObjectNode();
                com.fasterxml.jackson.databind.node.ArrayNode group = com.java_template.common.util.Json.mapper().createArrayNode();
                // Using the provided SearchConditionRequest is preferred but for brevity we use simple condition
                CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> fut = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    com.java_template.common.util.SearchConditionRequest.group("AND", com.java_template.common.util.Condition.of("$.email", "IEQUALS", user.getEmail())),
                    true
                );
                com.fasterxml.jackson.databind.node.ArrayNode found = fut.get();
                if (found != null && found.size() > 0) {
                    // If an existing user with same email exists (and different technicalId) -> error
                    boolean conflict = false;
                    for (int i = 0; i < found.size(); i++) {
                        ObjectNode n = (ObjectNode) found.get(i);
                        if (n.has("technicalId")) {
                            String tid = n.get("technicalId").asText();
                            String currentTid = context.attributes() != null ? (String) context.attributes().get("technicalId") : null;
                            if (currentTid == null || !currentTid.equals(tid)) {
                                conflict = true; break;
                            }
                        }
                    }
                    if (conflict) errors.add("email already exists");
                }

            } catch (Exception ex) {
                logger.warn("Could not perform email uniqueness check", ex);
            }

            if (!errors.isEmpty()) {
                user.setStatus("ERROR");
                ObjectNode node = com.java_template.common.util.Json.mapper().createObjectNode();
                node.putArray("errors");
                for (String e : errors) node.withArray("errors").add(e);
                CompletableFuture<UUID> fut = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    user
                );
                fut.whenComplete((id, ex) -> {
                    if (ex != null) logger.error("Failed to persist user with errors", ex);
                });
            } else {
                // Hash password if it appears to be plaintext (very naive check: length < 60)
                if (user.getPasswordHash() != null && user.getPasswordHash().length() < 60) {
                    // Using MD5 only as placeholder (DO NOT use in production). Real implementation would use bcrypt.
                    user.setPasswordHash(DigestUtils.md5DigestAsHex(user.getPasswordHash().getBytes()));
                }
                user.setStatus("ACTIVE");
                String now = Instant.now().toString();
                if (user.getCreatedAt() == null || user.getCreatedAt().isBlank()) user.setCreatedAt(now);
                user.setUpdatedAt(now);

                CompletableFuture<UUID> fut = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    user
                );
                fut.whenComplete((id, ex) -> {
                    if (ex != null) logger.error("Failed to persist activated user", ex);
                    else {
                        // Optionally initialize customer profile if role == Customer
                        if ("Customer".equalsIgnoreCase(user.getRole())) {
                            // For prototype, we just log. Real impl would create profile object stored in user.metadata or separate store.
                            logger.info("Initialize profile placeholders for customer {}", user.getId());
                        }
                    }
                });
            }
        } catch (Exception ex) {
            logger.error("Error in user activation processor", ex);
            user.setStatus("ERROR");
        }

        return context;
    }
}
