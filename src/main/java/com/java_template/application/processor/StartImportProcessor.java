package com.java_template.application.processor;

import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StartImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartImportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.isValid();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob entity = context.entity();

        // Business logic: mark the import job as running.
        // Per requirements, do not call update on this entity via EntityService;
        // Cyoda will persist changes to this entity automatically.
        entity.setStatus("Running");

        return entity;
    }
}