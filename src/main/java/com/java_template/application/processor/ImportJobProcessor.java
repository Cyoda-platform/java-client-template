package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.entity.importtask.version_1.ImportTask;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import java.util.concurrent.TimeUnit;

@Component
public class ImportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final EntityService entityService;

    public ImportJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob job) {
        return job != null && job.getPayload() != null && !job.getPayload().trim().isEmpty();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        // Mark job in progress and set timestamps
        job.setStatus("IN_PROGRESS");
        job.setCreatedAt(job.getCreatedAt() == null ? Instant.now() : job.getCreatedAt());

        // Persist a HackerNewsItem representing this payload using entityService
        HackerNewsItem item = new HackerNewsItem();
        item.setOriginalJson(job.getPayload());
        item.setCreatedAt(Instant.now());

        try {
            CompletableFuture<UUID> addFuture = entityService.addItem(
                HackerNewsItem.ENTITY_NAME,
                String.valueOf(HackerNewsItem.ENTITY_VERSION),
                item
            );
            UUID hnTechnicalId = addFuture.get(10, TimeUnit.SECONDS);

            // Create ImportTask referencing the job and the created item
            ImportTask task = new ImportTask();
            task.setJobTechnicalId(job.getJobName());
            task.setHnItemId(item.getId());
            task.setStatus("QUEUED");
            task.setAttempts(0);
            task.setCreatedAt(Instant.now());

            CompletableFuture<UUID> taskAdd = entityService.addItem(
                ImportTask.ENTITY_NAME,
                String.valueOf(com.java_template.application.entity.importtask.version_1.ImportTask.ENTITY_VERSION),
                task
            );
            UUID taskTechnicalId = taskAdd.get(10, TimeUnit.SECONDS);

            logger.info("ImportJobProcessor created HackerNewsItem technicalId={} and ImportTask technicalId={}",
                hnTechnicalId, taskTechnicalId);

            // Update job counters
            job.setItemsCreatedCount(1);
            job.setStatus("IN_PROGRESS");
        } catch (Exception e) {
            logger.error("Failed to persist HackerNewsItem or ImportTask: {}", e.getMessage());
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now());
        }
        return job;
    }
}
