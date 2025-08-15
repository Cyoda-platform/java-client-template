package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.concurrent.CompletableFuture;

@Component
public class CreateImportTaskProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateImportTaskProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateImportTaskProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CreateImportTaskProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid ImportJob for creating tasks")
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
            // Here we assume the StartImportProcessor already created tasks; this processor ensures tasks_created state is accurate
            job.setStatus("TASKS_CREATED");
            ObjectNode node = objectMapper.valueToTree(job);
            CompletableFuture<java.util.UUID> jobPersist = entityService.addItem(
                ImportJob.ENTITY_NAME,
                String.valueOf(ImportJob.ENTITY_VERSION),
                node
            );
            jobPersist.whenComplete((uuid, ex) -> {
                if (ex != null) logger.error("Failed to persist ImportJob TASKS_CREATED {}: {}", job.getTechnicalId(), ex.getMessage());
                else logger.info("Updated ImportJob {} to TASKS_CREATED", job.getTechnicalId());
            });
        } catch (Exception e) {
            logger.error("Error in CreateImportTaskProcessor for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
        }
        return job;
    }
}
