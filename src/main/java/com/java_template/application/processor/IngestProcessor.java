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
import java.util.*;
import java.util.concurrent.CompletionException;

@Component
public class IngestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public IngestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            logger.info("Starting ingestion for job id={}, name={}", job.getId(), job.getName());
            job.setStartedAt(Instant.now().toString());
            job.setStatus("INGESTING");

            int processed = 0;
            // Business-friendly ingestion: support inlined sample payloads in job.parameters for testing and deterministic behavior
            if (job.getParameters() != null && job.getParameters().containsKey("sampleRecords")) {
                Object raw = job.getParameters().get("sampleRecords");
                if (raw instanceof List) {
                    List<?> raws = (List<?>) raw;
                    for (Object item : raws) {
                        try {
                            // Map each item to Laureate using ObjectMapper for flexibility
                            Laureate laureate = objectMapper.convertValue(item, Laureate.class);
                            // set ingestion metadata
                            laureate.setSourceFetchedAt(Instant.now().toString());
                            laureate.setStatus("RECEIVED");

                            // Persist the laureate (create)
                            try {
                                UUID technicalId = entityService.addItem(
                                    Laureate.ENTITY_NAME,
                                    String.valueOf(Laureate.ENTITY_VERSION),
                                    laureate
                                ).join();
                                logger.info("Persisted Laureate (ingest) id={}, technicalId={}", laureate.getId(), technicalId);
                            } catch (CompletionException ce) {
                                logger.error("Failed to persist laureate during ingest: {}", ce.getMessage(), ce);
                                // mark lastError on job but continue processing
                                job.setLastError(ce.getMessage());
                            }

                            processed++;
                        } catch (Exception e) {
                            logger.error("Failed to map/persist sample record during ingest: {}", e.getMessage(), e);
                            job.setLastError(e.getMessage());
                        }
                    }
                }
            } else {
                // No sampleRecords provided — in a full implementation this is where HTTP ingestion/pagination and rate-limit handling would happen.
                logger.info("No inline sampleRecords provided in job.parameters. Skipping external ingestion in this environment.");
            }

            job.setProcessedRecordsCount(processed);
            // Do not set terminal status here; downstream processors/criteria determine SUCCEEDED/FAILED
            logger.info("Ingress completed for job id={} processedRecords={}", job.getId(), processed);
        } catch (Exception e) {
            logger.error("Unexpected error in IngestProcessor: {}", e.getMessage(), e);
            job.setLastError(e.getMessage());
            job.setAttemptCount((job.getAttemptCount() == null ? 0 : job.getAttemptCount()) + 1);
            // leave status as INGESTING or set to FAILED depending on retry policy — orchestration handles retries
            job.setStatus("INGESTING");
        }
        return job;
    }
}
