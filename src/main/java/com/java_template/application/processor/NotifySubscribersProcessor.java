package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory,
                                      EntityService entityService,
                                      ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CoverPhoto for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CoverPhoto.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CoverPhoto entity) {
        return entity != null && entity.isValid();
    }

    private CoverPhoto processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CoverPhoto> context) {
        CoverPhoto entity = context.entity();

        // Business logic:
        // - Query Users where subscribed == true
        // - For each subscribed user, send a notification (represented here by logging)
        // - Update cover photo updatedAt timestamp so the entity reflects processing

        String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.subscribed", "EQUALS", "true")
            );

            CompletableFuture<ArrayNode> usersFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode usersArray = usersFuture.join();
            if (usersArray == null || usersArray.isEmpty()) {
                logger.info("No subscribed users found for CoverPhoto id={}", entity.getId());
            } else {
                for (int i = 0; i < usersArray.size(); i++) {
                    ObjectNode userNode = (ObjectNode) usersArray.get(i);
                    try {
                        User user = objectMapper.treeToValue(userNode, User.class);
                        if (user != null && Boolean.TRUE.equals(user.getSubscribed())) {
                            // Simulate sending notification to user (actual notification system not available here)
                            logger.info("Notify user {} <{}> about new cover photo id={} title={}",
                                user.getUserId(), user.getEmail(), entity.getId(), entity.getTitle());
                        } else {
                            logger.debug("Skipping non-subscribed or invalid user record: {}", userNode);
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to deserialize user node for notification: {}", userNode, ex);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Error while notifying subscribers for CoverPhoto id={}: {}", entity.getId(), ex.getMessage(), ex);
            // Do not modify ingestionStatus here; only update metadata to reflect processing attempt.
        }

        // Update metadata to reflect notification processing; Cyoda will persist the entity.
        entity.setUpdatedAt(now);

        return entity;
    }
}