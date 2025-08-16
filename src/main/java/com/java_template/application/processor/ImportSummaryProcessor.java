package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.importjob.version_1.ImportJob;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ImportSummaryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportSummaryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ImportSummaryProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing import summary for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid import job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob job) {
        return job != null && job.getJobId() != null && !job.getJobId().isEmpty();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            // finalize import summary
            if (job.getStatus() == null || "PROCESSING".equals(job.getStatus())) {
                job.setStatus(job.getErrorMessage() != null && !job.getErrorMessage().isEmpty() ? "FAILED" : "COMPLETED");
                try {
                    job.setCompletedAt(Instant.now().toString());
                } catch (Throwable ignore) {
                }
                logger.info("ImportJob {} summary processed, status={}", job.getJobId(), job.getStatus());
            }
            return job;
        } catch (Exception e) {
            logger.error("Error while finalizing import job {}", job == null ? "<null>" : job.getJobId(), e);
            if (job != null) {
                job.setStatus("FAILED");
                try {
                    job.setErrorMessage(e.getMessage());
                    job.setCompletedAt(Instant.now().toString());
                } catch (Throwable ignore) {
                }
            }
            return job;
        }
    }
}
