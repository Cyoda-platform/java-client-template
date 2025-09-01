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

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class StartMatchingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartMatchingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public StartMatchingProcessor(SerializerFactory serializerFactory,
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

        // Default to RUNNING when starting matching
        job.setStatus("RUNNING");

        // Ensure resultsPreview is initialized
        if (job.getResultsPreview() == null) {
            job.setResultsPreview(new ArrayList<>());
        }

        // Validate owner existence before starting the matching pipeline.
        try {
            String ownerId = job.getOwnerId();
            if (ownerId == null || ownerId.isBlank()) {
                logger.error("AdoptionJob {} has no ownerId. Failing job.", job.getId());
                job.setStatus("FAILED");
                job.setResultCount(0);
                job.setResultsPreview(new ArrayList<>());
                return job;
            }

            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(ownerId);
            } catch (IllegalArgumentException iae) {
                logger.error("AdoptionJob {} has invalid ownerId format: {}. Failing job.", job.getId(), ownerId);
                job.setStatus("FAILED");
                job.setResultCount(0);
                job.setResultsPreview(new ArrayList<>());
                return job;
            }

            // Use the single-argument EntityService.getItem(UUID) as per EntityService contract
            CompletableFuture<DataPayload> ownerFuture = entityService.getItem(ownerUuid);
            DataPayload ownerPayload = ownerFuture != null ? ownerFuture.get() : null;

            if (ownerPayload == null || ownerPayload.getData() == null) {
                logger.error("Owner with id {} not found for AdoptionJob {}. Failing job.", ownerId, job.getId());
                job.setStatus("FAILED");
                job.setResultCount(0);
                job.setResultsPreview(new ArrayList<>());
                return job;
            }

            Owner owner = objectMapper.treeToValue(ownerPayload.getData(), Owner.class);
            if (owner == null || !owner.isValid()) {
                logger.error("Owner {} is invalid for AdoptionJob {}. Failing job.", ownerId, job.getId());
                job.setStatus("FAILED");
                job.setResultCount(0);
                job.setResultsPreview(new ArrayList<>());
                return job;
            }

            // Owner exists and is valid. Keep job in RUNNING state.
            // MatchingProcessor (next in workflow) will execute the actual matching.
            job.setResultCount(0); // initialized; matching will populate later
            if (job.getResultsPreview() == null) {
                job.setResultsPreview(new ArrayList<>());
            }

            logger.info("AdoptionJob {} validated owner {} and set to RUNNING", job.getId(), ownerId);

        } catch (Exception e) {
            logger.error("Failed to start matching for AdoptionJob {}: {}", job.getId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setResultCount(0);
            job.setResultsPreview(new ArrayList<>());
        }

        return job;
    }
}