package com.java_template.application.processor;

import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
import com.java_template.application.entity.owner.version_1.Owner;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifyProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetImportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetImportJob.class)
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

    private boolean isValidEntity(PetImportJob entity) {
        return entity != null && entity.isValid();
    }

    private PetImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetImportJob> context) {
        PetImportJob entity = context.entity();

        // Business logic:
        // - When a PetImportJob reaches COMPLETED or FAILED, notify interested Owners/administrators.
        // - We will retrieve Owner entities and log notification attempts for owners that have contactEmail.
        // - Record a short summary into the job.errors field indicating notifications were attempted.
        if (entity == null) {
            logger.warn("PetImportJob entity is null in execution context");
            return entity;
        }

        String status = entity.getStatus();
        if (status == null) {
            logger.warn("PetImportJob {} has null status; skipping notification", entity.getRequestId());
            return entity;
        }

        String normalizedStatus = status.trim().toUpperCase();
        if (!"COMPLETED".equals(normalizedStatus) && !"FAILED".equals(normalizedStatus)) {
            // Only notify on completion or failure
            logger.debug("PetImportJob {} status is {}; notifications not required", entity.getRequestId(), status);
            return entity;
        }

        String summary = String.format("Import Job %s finished with status=%s, importedCount=%d, errors=%s",
            entity.getRequestId(),
            entity.getStatus(),
            entity.getImportedCount() != null ? entity.getImportedCount() : 0,
            entity.getErrors() != null ? entity.getErrors() : ""
        );

        int notifiedCount = 0;
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                Owner.ENTITY_NAME,
                Owner.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            if (dataPayloads != null && !dataPayloads.isEmpty()) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        Owner owner = objectMapper.treeToValue(payload.getData(), Owner.class);
                        if (owner != null && owner.getContactEmail() != null && !owner.getContactEmail().isBlank()) {
                            // In a real implementation, we'd call an email/SMS service.
                            // Here we log the intent to notify and count it.
                            logger.info("Notify owner '{}' <{}>: {}", owner.getName(), owner.getContactEmail(), summary);
                            notifiedCount++;
                        } else {
                            logger.debug("Owner '{}' does not have a contact email; skipping notification", owner != null ? owner.getName() : "unknown");
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to convert DataPayload to Owner for notification: {}", ex.getMessage(), ex);
                    }
                }
            } else {
                logger.info("No owners found to notify for PetImportJob {}", entity.getRequestId());
            }
        } catch (Exception ex) {
            logger.error("Failed to retrieve owners for notification: {}", ex.getMessage(), ex);
            // Append retrieval error to job.errors
            String existing = entity.getErrors() != null ? entity.getErrors() : "";
            String appended = existing.isBlank() ? ("notification_error:" + ex.getMessage()) : (existing + " | notification_error:" + ex.getMessage());
            entity.setErrors(appended);
            return entity;
        }

        // Record notification summary into the job entity so workflow persistence will capture it.
        String existingErrors = entity.getErrors() != null ? entity.getErrors() : "";
        String notifyNote = String.format("notificationsSent=%d", notifiedCount);
        String newErrors = existingErrors.isBlank() ? notifyNote : (existingErrors + " | " + notifyNote);
        entity.setErrors(newErrors);

        logger.info("Notifications attempted for PetImportJob {}: {} owners notified", entity.getRequestId(), notifiedCount);

        return entity;
    }
}