package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.application.entity.post.version_1.Post;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class GdprTransfer implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GdprTransfer.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public GdprTransfer(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
        if (user == null) return user;

        String userId = user.getUserId();
        if (userId == null || userId.isBlank()) {
            logger.warn("User has no userId, skipping GDPR transfer");
            return user;
        }

        try {
            // Build condition: find posts where ownerId == userId
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.ownerId", "EQUALS", userId)
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Post.ENTITY_NAME,
                String.valueOf(Post.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode postsArray = itemsFuture.join();
            if (postsArray != null) {
                for (JsonNode node : postsArray) {
                    try {
                        // Convert JSON node to Post entity
                        Post post = objectMapper.treeToValue(node, Post.class);
                        if (post == null) continue;

                        String originalOwner = post.getOwnerId();
                        // Transfer ownership to Admin
                        post.setOwnerId("Admin");

                        // Append audit for the post transfer
                        Audit postAudit = new Audit();
                        postAudit.setAudit_id(UUID.randomUUID().toString());
                        postAudit.setAction("gdpr_transfer");
                        postAudit.setActor_id("system");
                        postAudit.setEntity_ref(post.getId() + ":Post");
                        postAudit.setTimestamp(Instant.now().toString());
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("from", originalOwner);
                        metadata.put("to", "Admin");
                        postAudit.setMetadata(metadata);

                        // Persist audit entry
                        CompletableFuture<UUID> auditAddFuture = entityService.addItem(
                            Audit.ENTITY_NAME,
                            String.valueOf(Audit.ENTITY_VERSION),
                            postAudit
                        );
                        auditAddFuture.join();

                        // Update the post entity (allowed - not the triggering entity)
                        if (post.getId() != null && !post.getId().isBlank()) {
                            CompletableFuture<UUID> postUpdate = entityService.updateItem(
                                Post.ENTITY_NAME,
                                String.valueOf(Post.ENTITY_VERSION),
                                UUID.fromString(post.getId()),
                                post
                            );
                            postUpdate.join();
                        } else {
                            logger.warn("Post without id encountered while transferring owner for user {}. Skipping update.", userId);
                        }
                    } catch (Exception e) {
                        logger.error("Error processing post during GDPR transfer for user {}: {}", userId, e.getMessage(), e);
                    }
                }
            }

            // Update user GDPR state - this entity will be persisted by Cyoda automatically
            user.setGdprState("transferred");

            // Append audit for the user transfer
            try {
                Audit userAudit = new Audit();
                userAudit.setAudit_id(UUID.randomUUID().toString());
                userAudit.setAction("gdpr_transfer_user");
                userAudit.setActor_id("system");
                userAudit.setEntity_ref(userId + ":User");
                userAudit.setTimestamp(Instant.now().toString());
                Map<String, Object> uMeta = new HashMap<>();
                uMeta.put("transferred_to", "Admin");
                userAudit.setMetadata(uMeta);

                CompletableFuture<UUID> userAuditAdd = entityService.addItem(
                    Audit.ENTITY_NAME,
                    String.valueOf(Audit.ENTITY_VERSION),
                    userAudit
                );
                userAuditAdd.join();
            } catch (Exception e) {
                logger.error("Failed to append user audit for GDPR transfer of user {}: {}", userId, e.getMessage(), e);
            }

        } catch (Exception ex) {
            logger.error("Unexpected error during GDPR transfer for user {}: {}", userId, ex.getMessage(), ex);
        }

        return user;
    }
}