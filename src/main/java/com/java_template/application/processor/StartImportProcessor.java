package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.importtask.version_1.ImportTask;
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
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class StartImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StartImportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StartImportProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid ImportJob for start")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.getJobType() != null && !entity.getJobType().isBlank();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            job.setStatus("IN_PROGRESS");
            job.setStartedAt(Instant.now().toString());

            // Persist job status update
            ObjectNode jobNode = objectMapper.valueToTree(job);
            CompletableFuture<java.util.UUID> jobPersist = entityService.addItem(
                ImportJob.ENTITY_NAME,
                String.valueOf(ImportJob.ENTITY_VERSION),
                jobNode
            );
            jobPersist.whenComplete((uuid, ex) -> {
                if (ex != null) logger.error("Failed to persist ImportJob {}: {}", job.getTechnicalId(), ex.getMessage());
                else logger.info("Persisted ImportJob technicalId={}", uuid);
            });

            // Create ImportTask entities for each payload item
            String payload = job.getPayload();
            JsonNode payloadNode = objectMapper.readTree(payload);
            if (payloadNode.isArray()) {
                ArrayNode arr = (ArrayNode) payloadNode;
                Iterator<JsonNode> it = arr.elements();
                while (it.hasNext()) {
                    JsonNode item = it.next();
                    createTaskForItem(job, item.toString());
                }
            } else {
                createTaskForItem(job, payloadNode.toString());
            }

        } catch (Exception e) {
            logger.error("Error starting import job {}: {}", job.getTechnicalId(), e.getMessage(), e);
        }
        return job;
    }

    private void createTaskForItem(ImportJob job, String itemJson) {
        try {
            ImportTask task = new ImportTask();
            task.setJobTechnicalId(job.getTechnicalId());
            task.setAttemptNumber(0);
            task.setStatus("PENDING");
            task.setResult(null);
            task.setAttemptedAt(null);

            ObjectNode node = objectMapper.valueToTree(task);
            CompletableFuture<java.util.UUID> taskPersist = entityService.addItem(
                ImportTask.ENTITY_NAME,
                String.valueOf(ImportTask.ENTITY_VERSION),
                node
            );
            taskPersist.whenComplete((uuid, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist ImportTask for job {}: {}", job.getTechnicalId(), ex.getMessage());
                } else {
                    logger.info("Created ImportTask technicalId={} for job {}", uuid, job.getTechnicalId());
                }
            });
        } catch (Exception e) {
            logger.error("Error creating task for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
        }
    }
}
