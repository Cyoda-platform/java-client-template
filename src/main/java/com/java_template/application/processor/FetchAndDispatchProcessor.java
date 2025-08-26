package com.java_template.application.processor;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.weatherobservation.version_1.WeatherObservation;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class FetchAndDispatchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchAndDispatchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FetchAndDispatchProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
        // Business logic:
        // - Mark job as IN_PROGRESS for the fetch/dispatch stage
        // - Fetch raw records for configured locations (here we simulate/emit one observation per location)
        // - For each record, create a WeatherObservation entity via EntityService.addItem(...)
        // - Do not call update on the Job via EntityService (the workflow will persist job changes automatically)

        try {
            logger.info("FetchAndDispatchProcessor started for job: {}", job.getJobName());

            // Update job status to IN_PROGRESS (workflow will persist the change)
            job.setStatus("IN_PROGRESS");

            List<String> locations = job.getLocations();
            if (locations == null || locations.isEmpty()) {
                logger.warn("Job {} has no locations to fetch", job.getJobName());
                return job;
            }

            // Simulate fetch from external source using job.getSource() and job.getParameters()
            String source = job.getSource();
            logger.info("Fetching data from source '{}' for {} locations", source, locations.size());

            // Prepare timestamp for observations
            String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

            // Create and dispatch a WeatherObservation per location (simulation of raw records)
            List<CompletableFuture<?>> futures = locations.stream().map(loc -> {
                WeatherObservation obs = new WeatherObservation();
                obs.setObservationId(UUID.randomUUID().toString());
                obs.setLocationId(loc);
                obs.setRawSourceId(job.getId() != null ? job.getId() : null);
                obs.setTimestamp(now);
                // mark as not processed - downstream processors should enrich/process it
                obs.setProcessed(false);
                // other numeric fields left null (will be filled if raw data available/enrichment)

                logger.debug("Dispatching observation {} for location {}", obs.getObservationId(), loc);

                CompletableFuture<?> future = entityService.addItem(
                    WeatherObservation.ENTITY_NAME,
                    String.valueOf(WeatherObservation.ENTITY_VERSION),
                    obs
                ).whenComplete((id, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to dispatch observation for location {}: {}", loc, ex.getMessage());
                    } else {
                        logger.info("Dispatched observation {} for location {}, persisted id={}", obs.getObservationId(), loc, id);
                    }
                });
                return future;
            }).collect(Collectors.toList());

            // Optionally wait for all dispatches to be submitted to the entity service (non-blocking for real flows).
            // Here we wait briefly to ensure submission, but we don't need the resulting UUIDs for job state changes.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).exceptionally(ex -> {
                logger.error("One or more dispatch operations failed: {}", ex.getMessage());
                return null;
            });

            logger.info("FetchAndDispatchProcessor completed dispatch for job: {}", job.getJobName());
        } catch (Exception ex) {
            logger.error("Error in FetchAndDispatchProcessor for job {}: {}", job != null ? job.getJobName() : "unknown", ex.getMessage(), ex);
            // Do not throw; leave job state for downstream handling
        }

        return job;
    }
}