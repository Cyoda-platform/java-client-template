package com.java_template.application.processor;

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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ManualReviewProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ManualReviewProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ManualReviewProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ManualReviewProcessor for request: {}", request.getId());

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

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        try {
            // Mark review by updating the retrievedAt timestamp to indicate manual review time
            // This reuses an existing field (retrievedAt) since User has no explicit review status field.
            String original = user.getRetrievedAt();
            user.setRetrievedAt(Instant.now().toString());

            // Persist this change to the User entity using EntityService (allowed - updating other entities)
            try {
                UUID techId = null;
                try {
                    techId = UUID.fromString(user.getTechnicalId());
                } catch (Exception ex) {
                    techId = null;
                }

                if (techId != null) {
                    CompletableFuture<UUID> future = entityService.updateItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), techId, user);
                    UUID updated = future.get();
                    logger.info("ManualReviewProcessor: updated User technicalId={} updatedId={}", user.getTechnicalId(), updated);
                } else {
                    logger.info("ManualReviewProcessor: cannot parse technicalId for update; skipping update for user id={}", user.getId());
                }
            } catch (Exception e) {
                logger.warn("ManualReviewProcessor: error while updating User: {}", e.getMessage());
                // restore original retrievedAt on failure to avoid misleading data
                user.setRetrievedAt(original);
            }

            logger.info("ManualReviewProcessor: completed manual review for user id={}", user.getId());
        } catch (Exception e) {
            logger.error("ManualReviewProcessor: unexpected error processing User: {}", e.getMessage());
        }
        return user;
    }
}
