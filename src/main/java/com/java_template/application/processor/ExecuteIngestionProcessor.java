package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ExecuteIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ExecuteIngestionProcessor(SerializerFactory serializerFactory,
                                     EntityService entityService,
                                     ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        if (job == null) {
            logger.warn("Received null Job in execution context");
            return null;
        }

        // Update last run timestamp to now (ISO-8601 UTC)
        String nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atZone(ZoneOffset.UTC));
        job.setLastRunTimestamp(nowIso);

        // If job is not enabled, skip ingestion and mark appropriately
        if (Boolean.FALSE.equals(job.getEnabled())) {
            job.setStatus("SKIPPED");
            job.setLastResultSummary("Job is disabled; ingestion skipped.");
            logger.info("Job {} is disabled - skipping ingestion", job.getName());
            return job;
        }

        // Simulated ingestion: create one Laureate record based on the job sourceEndpoint.
        // In a real implementation this would fetch and iterate over remote records.
        Laureate laureate = new Laureate();
        try {
            // Build minimal valid Laureate according to entity.isValid() constraints.
            String externalId = (job.getSourceEndpoint() == null ? "unknown-source" : job.getSourceEndpoint())
                + "-" + Instant.now().toEpochMilli();
            laureate.setExternalId(externalId);
            laureate.setFullName("Imported Laureate from " + (job.getSourceEndpoint() == null ? "unknown" : job.getSourceEndpoint()));
            laureate.setPrizeCategory("General");
            laureate.setPrizeYear(Instant.now().atZone(ZoneOffset.UTC).getYear());
            laureate.setRawPayload("{}");
            String ts = nowIso;
            laureate.setFirstSeenTimestamp(ts);
            laureate.setLastSeenTimestamp(ts);
            laureate.setChangeSummary("ingested by job: " + (job.getName() == null ? "unknown" : job.getName()));

            // Persist the Laureate via EntityService (allowed: add other entities)
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                laureate
            );

            UUID createdId = null;
            try {
                createdId = idFuture.join();
            } catch (Exception e) {
                // If addItem failed, record error and mark job failed
                logger.error("Failed to persist Laureate for job {}: {}", job.getName(), e.getMessage(), e);
                job.setStatus("FAILED");
                job.setLastResultSummary("Failed to persist laureate: " + e.getMessage());
                return job;
            }

            logger.info("Created Laureate with id {} for job {}", createdId, job.getName());
            job.setStatus("COMPLETED");
            job.setLastResultSummary("Ingested 1 laureate, sample id: " + (createdId == null ? "unknown" : createdId.toString()));
        } catch (Exception ex) {
            logger.error("Ingestion processing failed for job {}: {}", job.getName(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            job.setLastResultSummary("Ingestion error: " + ex.getMessage());
        }

        return job;
    }
}