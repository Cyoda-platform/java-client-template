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
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class JobValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public JobValidationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        if (job == null) return null;

        String originalStatus = job.getStatus() != null ? job.getStatus() : "";

        // If job is in PENDING state we perform a safe reset of previous results for this validation run.
        if ("PENDING".equalsIgnoreCase(originalStatus)) {
            job.setResultCount(0);
            job.setResultsPreview(new ArrayList<>());
        }

        try {
            // Validate criteria JSON
            if (job.getCriteria() == null || job.getCriteria().isBlank()) {
                logger.warn("AdoptionJob {} has empty criteria", job.getId());
                job.setStatus("FAILED");
                return job;
            }
            JsonNode criteriaNode;
            try {
                criteriaNode = objectMapper.readTree(job.getCriteria());
                if (criteriaNode == null || criteriaNode.isNull()) {
                    logger.warn("AdoptionJob {} has empty/invalid criteria structure", job.getId());
                    job.setStatus("FAILED");
                    return job;
                }
            } catch (Exception e) {
                logger.warn("AdoptionJob {} criteria is not valid JSON: {}", job.getId(), e.getMessage());
                job.setStatus("FAILED");
                return job;
            }

            // Validate owner existence and basic validity
            if (job.getOwnerId() == null || job.getOwnerId().isBlank()) {
                logger.warn("AdoptionJob {} has no ownerId", job.getId());
                job.setStatus("FAILED");
                return job;
            }

            Owner ownerObj = null;
            try {
                UUID ownerUuid = UUID.fromString(job.getOwnerId());
                CompletableFuture<DataPayload> ownerFuture = entityService.getItem(ownerUuid);
                DataPayload ownerPayload = ownerFuture != null ? ownerFuture.get() : null;
                if (ownerPayload == null || ownerPayload.getData() == null) {
                    logger.warn("AdoptionJob {} references missing owner {}", job.getId(), job.getOwnerId());
                    job.setStatus("FAILED");
                    return job;
                }
                // Try to convert payload to Owner to validate its state
                try {
                    ownerObj = objectMapper.treeToValue(ownerPayload.getData(), Owner.class);
                    if (ownerObj == null || !ownerObj.isValid()) {
                        logger.warn("AdoptionJob {} owner {} is invalid", job.getId(), job.getOwnerId());
                        job.setStatus("FAILED");
                        return job;
                    }
                } catch (Exception e) {
                    // If deserialization fails but payload exists, treat as invalid owner
                    logger.warn("Failed to deserialize owner payload for AdoptionJob {} ownerId={}: {}", job.getId(), job.getOwnerId(), e.getMessage());
                    job.setStatus("FAILED");
                    return job;
                }
            } catch (IllegalArgumentException iae) {
                logger.warn("AdoptionJob {} ownerId is not a valid UUID: {}", job.getId(), job.getOwnerId());
                job.setStatus("FAILED");
                return job;
            } catch (Exception ex) {
                logger.error("Error while verifying owner for AdoptionJob {}: {}", job.getId(), ex.getMessage(), ex);
                job.setStatus("FAILED");
                return job;
            }

            // All validations passed -> advance to RUNNING only if original was PENDING
            if ("PENDING".equalsIgnoreCase(originalStatus)) {
                job.setStatus("RUNNING");
            } else {
                // If job was already in another active state, keep status but log
                logger.info("AdoptionJob {} validation passed but original status was '{}'; leaving status unchanged.", job.getId(), originalStatus);
            }
            return job;
        } catch (Exception e) {
            logger.error("Unexpected error validating AdoptionJob {}: {}", job != null ? job.getId() : "unknown", e.getMessage(), e);
            if (job != null) {
                job.setStatus("FAILED");
            }
            return job;
        }
    }
}