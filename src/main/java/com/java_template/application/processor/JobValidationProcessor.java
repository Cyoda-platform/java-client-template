package com.java_template.application.processor;
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

        // Default safe-reset of results for a validation run
        job.setResultCount(0);
        job.setResultsPreview(new ArrayList<>());

        // Only validate if job is in PENDING state, otherwise leave as-is but still perform checks
        try {
            // Validate criteria JSON
            try {
                JsonNode criteriaNode = objectMapper.readTree(job.getCriteria());
                if (criteriaNode == null || criteriaNode.isNull()) {
                    logger.warn("AdoptionJob {} has empty/invalid criteria", job.getId());
                    job.setStatus("FAILED");
                    return job;
                }
            } catch (Exception e) {
                logger.warn("AdoptionJob {} criteria is not valid JSON: {}", job.getId(), e.getMessage());
                job.setStatus("FAILED");
                return job;
            }

            // Validate owner existence
            try {
                UUID ownerUuid = UUID.fromString(job.getOwnerId());
                CompletableFuture<DataPayload> ownerFuture = entityService.getItem(ownerUuid);
                DataPayload ownerPayload = ownerFuture != null ? ownerFuture.get() : null;
                if (ownerPayload == null || ownerPayload.getData() == null) {
                    logger.warn("AdoptionJob {} references missing owner {}", job.getId(), job.getOwnerId());
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

            // All validations passed -> advance to RUNNING
            job.setStatus("RUNNING");
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