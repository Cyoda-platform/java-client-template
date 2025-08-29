package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.application.entity.post.version_1.Post;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class MarkErasurePending implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkErasurePending.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public MarkErasurePending(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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

        // Business logic: mark user as GDPR erasure pending, append audit for user,
        // and transfer ownership of user's posts to "Admin" with audit entries.
        try {
            // 1. Mark user GDPR state to erased_pending
            user.setGdprState("erased_pending");
            logger.info("User {} marked as erasure pending", user.getUserId());

            // 2. Append an Audit entry for the user erasure request
            Audit userAudit = new Audit();
            userAudit.setAuditId(UUID.randomUUID().toString());
            userAudit.setAction("gdpr_erasure_requested");
            userAudit.setActorId(user.getUserId() != null ? user.getUserId() : "unknown");
            userAudit.setEntityRef(user.getUserId() + ":User");
            userAudit.setTimestamp(Instant.now().toString());
            Map<String, Object> userMeta = new HashMap<>();
            userMeta.put("reason", "user_requested_erasure");
            userAudit.setMetadata(userMeta);

            try {
                CompletableFuture<UUID> addedAuditFuture = entityService.addItem(
                    Audit.ENTITY_NAME,
                    Audit.ENTITY_VERSION,
                    userAudit
                );
                UUID auditId = addedAuditFuture.get();
                logger.info("Appended user audit {} for user {}", auditId, user.getUserId());
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Failed to persist user audit for user {}: {}", user.getUserId(), ex.getMessage(), ex);
            }

            // 3. Find posts owned by the user and transfer ownership to "Admin"
            try {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.owner_id", "EQUALS", user.getUserId())
                );

                CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Post.ENTITY_NAME,
                    Post.ENTITY_VERSION,
                    condition,
                    true
                );

                List<DataPayload> dataPayloads = filteredItemsFuture.get();
                if (dataPayloads != null) {
                    for (DataPayload payload : dataPayloads) {
                        try {
                            // Convert payload data to Post
                            Post post = objectMapper.treeToValue(payload.getData(), Post.class);
                            // Extract technicalId from meta for update
                            String technicalId = payload.getMeta().get("entityId").asText();

                            // Update owner_id to Admin
                            post.setOwner_id("Admin");

                            // Persist update
                            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                                UUID.fromString(technicalId),
                                post
                            );
                            UUID updatedId = updatedIdFuture.get();
                            logger.info("Transferred post {} to Admin (technicalId={})", post.getId(), updatedId);

                            // Append audit for the post transfer
                            Audit postAudit = new Audit();
                            postAudit.setAuditId(UUID.randomUUID().toString());
                            postAudit.setAction("gdpr_transfer");
                            postAudit.setActorId("system");
                            postAudit.setEntityRef(post.getId() + ":Post");
                            postAudit.setTimestamp(Instant.now().toString());
                            Map<String, Object> meta = new HashMap<>();
                            meta.put("from", user.getUserId());
                            meta.put("to", "Admin");
                            postAudit.setMetadata(meta);

                            try {
                                CompletableFuture<UUID> addedPostAuditFuture = entityService.addItem(
                                    Audit.ENTITY_NAME,
                                    Audit.ENTITY_VERSION,
                                    postAudit
                                );
                                UUID postAuditId = addedPostAuditFuture.get();
                                logger.info("Appended post audit {} for post {}", postAuditId, post.getId());
                            } catch (InterruptedException | ExecutionException ex) {
                                logger.error("Failed to persist post audit for post {}: {}", post.getId(), ex.getMessage(), ex);
                            }

                        } catch (Exception ex) {
                            logger.error("Failed to process post payload for user {}: {}", user.getUserId(), ex.getMessage(), ex);
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Failed to retrieve posts for user {}: {}", user.getUserId(), ex.getMessage(), ex);
            }

        } catch (Exception ex) {
            logger.error("Unexpected error in mark erasure pending for user {}: {}", user != null ? user.getUserId() : "unknown", ex.getMessage(), ex);
        }

        return user;
    }
}