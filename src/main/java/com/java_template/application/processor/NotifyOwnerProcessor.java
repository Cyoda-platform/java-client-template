package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
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

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class NotifyOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifyOwnerProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
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

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.isValid();
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner owner = context.entity();

        // Business logic:
        // - If owner has contactEmail, attempt to locate pending AdoptionJob(s) created for this owner
        // - For each found AdoptionJob, append a small marker to resultsPreview indicating a notification was sent to the owner's email
        // - Update resultCount to reflect the modified resultsPreview and persist the AdoptionJob
        // - If no contactEmail provided, just log and return owner unchanged

        if (owner == null) {
            logger.warn("Owner entity is null in processEntityLogic");
            return null;
        }

        String contactEmail = owner.getContactEmail();
        if (contactEmail == null || contactEmail.isBlank()) {
            logger.warn("Owner {} has no contactEmail; skipping notification steps", owner.getId());
            return owner;
        }

        // Build search condition: ownerId == owner.getId() AND status == "PENDING"
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$.ownerId", "EQUALS", owner.getId()),
            Condition.of("$.status", "EQUALS", "PENDING")
        );

        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                AdoptionJob.ENTITY_NAME,
                AdoptionJob.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = itemsFuture.get();
            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.info("No pending AdoptionJob found for owner {}", owner.getId());
                return owner;
            }

            for (DataPayload payload : dataPayloads) {
                try {
                    AdoptionJob job = objectMapper.treeToValue(payload.getData(), AdoptionJob.class);
                    if (job == null) {
                        logger.warn("Failed to deserialize AdoptionJob payload for owner {}", owner.getId());
                        continue;
                    }

                    // Ensure resultsPreview list exists
                    List<String> preview = job.getResultsPreview();
                    if (preview == null) {
                        preview = new ArrayList<>();
                        job.setResultsPreview(preview);
                    }

                    // Add notification marker
                    String marker = "notificationSentTo:" + contactEmail;
                    preview.add(marker);
                    job.setResultCount(preview.size());

                    // Persist update to the AdoptionJob (we are allowed to update other entities)
                    String technicalId = null;
                    if (payload.getMeta() != null && payload.getMeta().get("entityId") != null) {
                        technicalId = payload.getMeta().get("entityId").asText();
                    }

                    if (technicalId != null && !technicalId.isBlank()) {
                        try {
                            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(UUID.fromString(technicalId), job);
                            UUID updated = updatedIdFuture.get();
                            logger.info("Updated AdoptionJob {} after notifying owner {} (marker={})", updated, owner.getId(), marker);
                        } catch (InterruptedException | ExecutionException e) {
                            logger.error("Failed to update AdoptionJob for owner {}: {}", owner.getId(), e.getMessage(), e);
                        }
                    } else {
                        logger.warn("AdoptionJob payload missing technical entityId meta; cannot update job for owner {}", owner.getId());
                    }

                } catch (Exception e) {
                    logger.error("Error processing AdoptionJob payload for owner {}: {}", owner.getId(), e.getMessage(), e);
                }
            }

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to query AdoptionJob for owner {}: {}", owner.getId(), e.getMessage(), e);
        }

        // No direct modifications to Owner entity are necessary for notification; return as-is
        return owner;
    }
}