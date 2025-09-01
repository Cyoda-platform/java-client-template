package com.java_template.application.processor;

import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifyResultsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyResultsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifyResultsProcessor(SerializerFactory serializerFactory,
                                  EntityService entityService,
                                  ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionJob.class)
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

    private boolean isValidEntity(AdoptionJob entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionJob> context) {
        AdoptionJob job = context.entity();
        if (job == null) {
            logger.warn("Received null AdoptionJob in NotifyResultsProcessor");
            return job;
        }

        // Only notify when job has completed
        if (!"COMPLETED".equalsIgnoreCase(job.getStatus())) {
            logger.info("AdoptionJob {} is not in COMPLETED state (current: {}), skipping notification.", job.getId(), job.getStatus());
            return job;
        }

        String ownerId = job.getOwnerId();
        if (ownerId == null || ownerId.isBlank()) {
            logger.error("AdoptionJob {} has no ownerId, cannot notify owner.", job.getId());
            job.setStatus("FAILED");
            return job;
        }

        try {
            // Fetch owner entity by technical id (ownerId expected to be serialized UUID or string)
            CompletableFuture<DataPayload> ownerFuture = entityService.getItem(UUID.fromString(ownerId));
            DataPayload ownerPayload = ownerFuture.get();

            if (ownerPayload == null || ownerPayload.getData() == null) {
                logger.error("Owner with id {} not found for AdoptionJob {}", ownerId, job.getId());
                job.setStatus("FAILED");
                return job;
            }

            Owner owner = objectMapper.treeToValue(ownerPayload.getData(), Owner.class);

            // Compose notification message (simulation)
            String ownerName = owner.getName() != null ? owner.getName() : "Owner";
            String contact = owner.getContactEmail() != null ? owner.getContactEmail() : owner.getPhone();
            String preview = job.getResultsPreview() != null ? job.getResultsPreview().toString() : "[]";
            String message = String.format("Hello %s, your adoption job %s completed with %d results: %s",
                    ownerName, job.getId(), job.getResultCount() != null ? job.getResultCount() : 0, preview);

            // Log the notification (actual sending could be implemented via HttpClient if configured)
            logger.info("NotifyResultsProcessor: notifying ownerId={} contact={} message={}", owner.getId(), contact, message);

            // Mark job as NOTIFIED to indicate notification step completed
            job.setStatus("NOTIFIED");
            return job;
        } catch (Exception e) {
            logger.error("Failed to notify owner for AdoptionJob {}: {}", job.getId(), e.getMessage(), e);
            job.setStatus("FAILED");
            return job;
        }
    }
}