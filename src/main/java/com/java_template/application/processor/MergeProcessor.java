package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.userrecord.version_1.UserRecord;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class MergeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MergeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MergeProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MergeProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(UserRecord.class)
            .validate(this::isValidEntity, "Invalid user record for merge")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(UserRecord user) {
        return user != null && user.isValid();
    }

    private UserRecord processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<UserRecord> context) {
        UserRecord user = context.entity();
        try {
            // Find existing by externalId or email
            ObjectNode existing = null;
            if (user.getExternalId() != null) {
                CompletableFuture<ObjectNode> future = entityService.getItemsByCondition(
                    UserRecord.ENTITY_NAME,
                    String.valueOf(UserRecord.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.externalId", "EQUALS", String.valueOf(user.getExternalId()))),
                    true
                );
                try { existing = (ObjectNode) future.join().get(0); } catch (Exception ignored) {}
            }

            if (existing == null && user.getEmail() != null) {
                CompletableFuture<ObjectNode> future = entityService.getItemsByCondition(
                    UserRecord.ENTITY_NAME,
                    String.valueOf(UserRecord.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.email", "IEQUALS", user.getEmail())),
                    true
                );
                try { existing = (ObjectNode) future.join().get(0); } catch (Exception ignored) {}
            }

            if (existing != null) {
                // Merge non-null fields
                if ((user.getFirstName() == null || user.getFirstName().isBlank()) && existing.hasNonNull("firstName")) {
                    user.setFirstName(existing.get("firstName").asText());
                }
                if ((user.getLastName() == null || user.getLastName().isBlank()) && existing.hasNonNull("lastName")) {
                    user.setLastName(existing.get("lastName").asText());
                }

                if (existing.hasNonNull("storedAt")) {
                    user.setStoredAt(existing.get("storedAt").asText());
                }

                // Update existing via entityService
                try {
                    UUID technicalId = UUID.fromString(existing.get("technicalId").asText());
                    CompletableFuture<UUID> fut = entityService.updateItem(
                        UserRecord.ENTITY_NAME,
                        String.valueOf(UserRecord.ENTITY_VERSION),
                        technicalId,
                        user
                    );
                    fut.join();
                } catch (Exception ex) {
                    logger.warn("Failed to update existing UserRecord during merge", ex);
                }

                user.setStatus("STORED");
                user.setStoredAt(Instant.now().toString());
                logger.info("Merged UserRecord externalId={} with existing record", user.getExternalId());
                return user;
            }

            // No existing record found - nothing to merge, mark as STORED by PersistUserProcessor
            user.setStatus("STORED");
            user.setStoredAt(Instant.now().toString());
            return user;
        } catch (Exception ex) {
            logger.error("Unexpected error during MergeProcessor", ex);
            user.setStatus("ERROR");
            user.setErrorMessage("Merge error: " + ex.getMessage());
            return user;
        }
    }
}
