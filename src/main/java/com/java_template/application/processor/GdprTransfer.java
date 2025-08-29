package com.java_template.application.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.application.entity.post.version_1.Post;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

        // Business logic: transfer ownership of posts owned by this user to "Admin",
        // append audit entries for each transferred post and for the user, and set user.gdprState = "transferred".
        try {
            // Build condition to find posts where owner_id equals this user's userId
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.owner_id", "EQUALS", user.getUserId())
            );

            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                Post.ENTITY_NAME,
                Post.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = itemsFuture.get();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        // Convert payload data to Post entity
                        Post post = objectMapper.treeToValue(payload.getData(), Post.class);

                        // Extract technical id for update
                        String technicalId = null;
                        if (payload.getMeta() != null && payload.getMeta().get("entityId") != null) {
                            try {
                                technicalId = payload.getMeta().get("entityId").asText();
                            } catch (Exception ex) {
                                logger.warn("Unable to read technical id from payload meta for post {}: {}", post != null ? post.getId() : "<unknown>", ex.getMessage());
                            }
                        }

                        if (post == null) {
                            logger.warn("Skipping null post payload during GDPR transfer.");
                            continue;
                        }

                        // Change ownership to Admin
                        post.setOwner_id("Admin");

                        // Create audit entry for post transfer
                        Audit postAudit = new Audit();
                        postAudit.setAuditId(UUID.randomUUID().toString());
                        postAudit.setAction("gdpr_transfer");
                        postAudit.setActorId("system");
                        String entityRef = (post.getId() != null ? post.getId() : "") + ":Post";
                        postAudit.setEntityRef(entityRef);
                        postAudit.setTimestamp(Instant.now().toString());
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("from", user.getUserId());
                        meta.put("to", "Admin");
                        postAudit.setMetadata(meta);

                        // Persist audit entry
                        try {
                            CompletableFuture<UUID> auditAdd = entityService.addItem(Audit.ENTITY_NAME, Audit.ENTITY_VERSION, postAudit);
                            auditAdd.get();
                        } catch (Exception ex) {
                            logger.error("Failed to add audit for post {}: {}", post.getId(), ex.getMessage(), ex);
                        }

                        // Update the post entity using its technical id
                        if (technicalId != null && !technicalId.isBlank()) {
                            try {
                                CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), post);
                                updated.get();
                            } catch (Exception ex) {
                                logger.error("Failed to update post {} during GDPR transfer: {}", post.getId(), ex.getMessage(), ex);
                            }
                        } else {
                            logger.warn("Missing technicalId for post {}, cannot update ownership. Skipping update.", post.getId());
                        }
                    } catch (JsonProcessingException jpe) {
                        logger.error("Failed to parse post payload during GDPR transfer: {}", jpe.getMessage(), jpe);
                    }
                }
            }

            // Update user gdpr state (the triggering entity will be persisted by Cyoda automatically)
            user.setGdprState("transferred");

            // Append audit entry for the user transfer
            Audit userAudit = new Audit();
            userAudit.setAuditId(UUID.randomUUID().toString());
            userAudit.setAction("gdpr_transfer_user");
            userAudit.setActorId("system");
            userAudit.setEntityRef((user.getUserId() != null ? user.getUserId() : "") + ":User");
            userAudit.setTimestamp(Instant.now().toString());
            Map<String, Object> userMeta = new HashMap<>();
            userMeta.put("note", "User GDPR transfer completed");
            userAudit.setMetadata(userMeta);

            try {
                CompletableFuture<UUID> added = entityService.addItem(Audit.ENTITY_NAME, Audit.ENTITY_VERSION, userAudit);
                added.get();
            } catch (Exception ex) {
                logger.error("Failed to add user audit for GDPR transfer for user {}: {}", user.getUserId(), ex.getMessage(), ex);
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("GDPR transfer interrupted for user {}: {}", user.getUserId(), ie.getMessage(), ie);
        } catch (ExecutionException ee) {
            logger.error("Execution error during GDPR transfer for user {}: {}", user.getUserId(), ee.getMessage(), ee);
        } catch (Exception ex) {
            logger.error("Unexpected error during GDPR transfer for user {}: {}", user.getUserId(), ex.getMessage(), ex);
        }

        return user;
    }
}