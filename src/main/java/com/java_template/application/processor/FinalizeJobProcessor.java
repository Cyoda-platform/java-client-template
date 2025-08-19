package com.java_template.application.processor;

import com.java_template.application.entity.fetchjob.version_1.FetchJob;
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

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class FinalizeJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public FinalizeJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FinalizeJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(FetchJob.class)
                .validate(this::isValidEntity, "Invalid FetchJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FetchJob job) {
        return job != null && job.isValid();
    }

    private FetchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FetchJob> context) {
        FetchJob job = context.entity();
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            job.setLastRunAt(now);

            // compute nextRunAt based on recurrence and runDay/runTime
            try {
                if (job.getRecurrence() != null && job.getRecurrence().equalsIgnoreCase("weekly")) {
                    // schedule next run on next occurrence of runDay
                    java.time.DayOfWeek day = java.time.DayOfWeek.valueOf(job.getRunDay().toUpperCase());
                    java.time.ZonedDateTime znow = now.atZoneSameInstant(ZoneId.of(job.getTimezone() == null ? "UTC" : job.getTimezone()));
                    java.time.ZonedDateTime candidate = znow.with(java.time.temporal.TemporalAdjusters.nextOrSame(day));
                    // parse runTime HH:mm
                    if (job.getRunTime() != null) {
                        String[] parts = job.getRunTime().split(":" );
                        int hour = Integer.parseInt(parts[0]);
                        int min = Integer.parseInt(parts[1]);
                        candidate = candidate.withHour(hour).withMinute(min).withSecond(0).withNano(0);
                        job.setNextRunAt(candidate.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime());
                    } else {
                        job.setNextRunAt(candidate.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime());
                    }
                }
            } catch (Exception ex) {
                logger.warn("FinalizeJobProcessor: unable to compute nextRunAt", ex);
            }

            job.setStatus("completed");

            // persist update to fetchJob
            // must use entityService.updateItem to update the job entity, using technicalId from context request
            if (context.request() != null && context.request().getEntityId() != null) {
                try {
                    java.util.UUID id = java.util.UUID.fromString(context.request().getEntityId());
                    CompletableFuture<java.util.UUID> fut = entityService.updateItem(FetchJob.ENTITY_NAME, String.valueOf(FetchJob.ENTITY_VERSION), id, job);
                    fut.get();
                } catch (InterruptedException | ExecutionException ex) {
                    logger.warn("FinalizeJobProcessor: failed to persist fetchJob update", ex);
                }
            }
        } catch (Exception ex) {
            logger.error("FinalizeJobProcessor: unexpected error", ex);
            job.setStatus("failed");
        }
        return job;
    }
}
