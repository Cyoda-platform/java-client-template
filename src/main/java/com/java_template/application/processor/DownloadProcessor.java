package com.java_template.application.processor;

import com.java_template.application.entity.datadownloadjob.version_1.DataDownloadJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DownloadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DownloadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DownloadProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataDownloadJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataDownloadJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataDownloadJob entity) {
        return entity != null && entity.getUrl() != null && !entity.getUrl().isEmpty()
               && entity.getStatus() != null && !entity.getStatus().isEmpty();
    }

    private DataDownloadJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataDownloadJob> context) {
        DataDownloadJob entity = context.entity();
        // Business logic implementation:
        // Based on functional requirements, simulate the download process
        // Set status to IN_PROGRESS, then simulate completion

        if ("PENDING".equalsIgnoreCase(entity.getStatus())) {
            entity.setStatus("IN_PROGRESS");
            logger.info("DownloadProcessor set job status to IN_PROGRESS for URL: {}", entity.getUrl());
        } else if ("IN_PROGRESS".equalsIgnoreCase(entity.getStatus())) {
            // Simulate download success
            entity.setStatus("COMPLETED");
            entity.setCompletedAt(java.time.Instant.now().toString());
            logger.info("DownloadProcessor set job status to COMPLETED for URL: {}", entity.getUrl());
        }

        return entity;
    }
}