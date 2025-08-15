package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifyJobCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyJobCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NotifyJobCompletionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotifyJobCompletionProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid ImportJob for notification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null;
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            job.setFinishedAt(Instant.now().toString());
            job.setStatus("NOTIFIED");

            ObjectNode node = objectMapper.valueToTree(job);
            CompletableFuture<java.util.UUID> jobPersist = entityService.addItem(
                ImportJob.ENTITY_NAME,
                String.valueOf(ImportJob.ENTITY_VERSION),
                node
            );
            jobPersist.whenComplete((uuid, ex) -> {
                if (ex != null) logger.error("Failed to persist notified ImportJob {}: {}", job.getTechnicalId(), ex.getMessage());
                else logger.info("Persisted notified ImportJob {}", uuid);
            });

            // Optionally publish event - omitted as external event bus is not available here

        } catch (Exception e) {
            logger.error("Error notifying completion for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
        }
        return job;
    }
}
