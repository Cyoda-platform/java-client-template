package com.java_template.application.processor;

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
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ExecuteIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ExecuteIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        String timestamp = Instant.now().toString();

        try {
            // If job is not enabled, skip ingestion but update state accordingly.
            if (job.getEnabled() == null || !job.getEnabled()) {
                logger.info("Job {} is not enabled. Skipping ingestion.", job.getName());
                job.setStatus("SKIPPED");
                job.setLastResultSummary("Job skipped because it is not enabled.");
                job.setLastRunTimestamp(timestamp);
                return job;
            }

            // Simulate fetching records from the job.sourceEndpoint.
            // Real implementation would call external API based on job.getSourceEndpoint().
            // Here we create a simple normalized Laureate entity from job metadata to trigger downstream workflow.
            List<Laureate> toPersist = new ArrayList<>();

            Laureate laureate = new Laureate();
            // Use available getters on Job to populate some fields for the Laureate.
            String source = job.getSourceEndpoint() != null ? job.getSourceEndpoint() : "unknown_source";
            String namePart = job.getName() != null ? job.getName() : "ingest";

            laureate.setExternalId(source + ":" + namePart + ":" + timestamp);
            laureate.setFullName("Imported records from " + source);
            laureate.setPrizeCategory("Unknown"); // required non-blank value
            laureate.setPrizeYear(Year.now().getValue()); // required value
            laureate.setRawPayload("{\"ingestedFrom\":\"" + source + "\"}");
            laureate.setFirstSeenTimestamp(timestamp);
            laureate.setLastSeenTimestamp(timestamp);
            laureate.setChangeSummary("Ingested by job: " + job.getName());
            laureate.setCountry(null);
            laureate.setBirthDate(null);
            laureate.setMotivation(null);

            toPersist.add(laureate);

            // Persist each laureate using entityService.addItem -> triggers Laureate workflow automatically
            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (Laureate l : toPersist) {
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    l
                );
                futures.add(idFuture);
            }

            // Wait for all add operations to complete
            CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .join();

            // Update job state to completed
            job.setStatus("COMPLETED");
            job.setLastResultSummary("Ingested " + toPersist.size() + " records from " + source);
            job.setLastRunTimestamp(timestamp);
            logger.info("Job {} completed ingestion of {} records.", job.getName(), toPersist.size());
        } catch (Exception ex) {
            logger.error("Error during ingestion for job {}: {}", job.getName(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            job.setLastResultSummary("Ingestion failed: " + ex.getMessage());
            job.setLastRunTimestamp(timestamp);
        }

        return job;
    }
}