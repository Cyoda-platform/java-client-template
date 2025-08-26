package com.java_template.application.processor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.searchfilter.version_1.SearchFilter;
import com.java_template.application.entity.transformjob.version_1.TransformJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class QueueJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QueueJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public QueueJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing TransformJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(TransformJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(TransformJob entity) {
        return entity != null && entity.isValid();
    }

    private TransformJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<TransformJob> context) {
        TransformJob job = context.entity();

        try {
            // Only queue jobs that are in initial PENDING state; otherwise leave as-is
            String currentStatus = job.getStatus();
            if (currentStatus == null) {
                logger.warn("TransformJob {} has null status, failing job", job.getId());
                job.setStatus("FAILED");
                job.setErrorMessage("Missing job status");
                job.setCompletedAt(Instant.now().toString());
                return job;
            }

            if (!"PENDING".equalsIgnoreCase(currentStatus)) {
                logger.info("TransformJob {} is not in PENDING state (current: {}), skipping queue", job.getId(), currentStatus);
                return job;
            }

            // Validate presence of referenced SearchFilter
            String filterId = job.getSearchFilterId();
            if (filterId == null || filterId.isBlank()) {
                logger.error("TransformJob {} missing searchFilterId, failing job", job.getId());
                job.setStatus("FAILED");
                job.setErrorMessage("Missing searchFilterId");
                job.setCompletedAt(Instant.now().toString());
                return job;
            }

            // Attempt to read the referenced SearchFilter entity
            try {
                CompletableFuture<ObjectNode> filterFuture = entityService.getItem(
                    SearchFilter.ENTITY_NAME,
                    String.valueOf(SearchFilter.ENTITY_VERSION),
                    UUID.fromString(filterId)
                );

                ObjectNode filterNode = filterFuture.join();
                if (filterNode == null || filterNode.isEmpty()) {
                    logger.error("TransformJob {} referenced SearchFilter {} not found, failing job", job.getId(), filterId);
                    job.setStatus("FAILED");
                    job.setErrorMessage("Referenced SearchFilter not found");
                    job.setCompletedAt(Instant.now().toString());
                    return job;
                }
            } catch (Exception e) {
                logger.error("Error while retrieving SearchFilter {} for TransformJob {}: {}", filterId, job.getId(), e.getMessage());
                job.setStatus("FAILED");
                job.setErrorMessage("Error retrieving referenced SearchFilter: " + e.getMessage());
                job.setCompletedAt(Instant.now().toString());
                return job;
            }

            // All checks passed: mark job as QUEUED
            job.setStatus("QUEUED");
            // Clear any previous errors and ensure output/completion metadata reset for a new run
            job.setErrorMessage(null);
            job.setOutputLocation(null);
            job.setCompletedAt(null);
            // startedAt remains unset until the job actually starts RUNNING
            logger.info("TransformJob {} queued successfully", job.getId());
            return job;

        } catch (Exception ex) {
            logger.error("Unexpected error while queueing TransformJob {}: {}", job != null ? job.getId() : "unknown", ex.getMessage());
            if (job != null) {
                job.setStatus("FAILED");
                job.setErrorMessage("Unexpected error: " + ex.getMessage());
                job.setCompletedAt(Instant.now().toString());
            }
            return job;
        }
    }
}