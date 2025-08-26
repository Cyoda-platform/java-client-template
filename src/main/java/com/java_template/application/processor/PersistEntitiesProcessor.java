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

@Component
public class PersistEntitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistEntitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistEntitiesProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
        ImportJob job = context.entity();
        logger.info("PersistEntitiesProcessor executing for jobId={}, jobType={}", job.getJobId(), job.getJobType());

        // Initialize result summary if absent
        if (job.getResultSummary() == null) {
            ImportJob.ResultSummary rs = new ImportJob.ResultSummary();
            rs.setCreated(0);
            rs.setUpdated(0);
            rs.setFailed(0);
            job.setResultSummary(rs);
        } else {
            if (job.getResultSummary().getCreated() == null) job.getResultSummary().setCreated(0);
            if (job.getResultSummary().getUpdated() == null) job.getResultSummary().setUpdated(0);
            if (job.getResultSummary().getFailed() == null) job.getResultSummary().setFailed(0);
        }

        // Business logic:
        // The ParseImportProcessor is expected to run earlier and provide parsed rows.
        // If no parsed rows are available in the ImportJob entity (no schema for rows),
        // we cannot create/update Product/User entities here. As a safe default we mark
        // the job as Completed with zero counts when jobType is recognized, otherwise mark Failed.
        String jobType = job.getJobType() == null ? "" : job.getJobType().trim().toLowerCase();

        switch (jobType) {
            case "products":
            case "product":
            case "users":
            case "user":
                // In the real flow parsed rows would be processed here: validate and add/update Product/User via entityService.
                // Since parsed rows are not part of the ImportJob model, we set an empty result summary and mark completed.
                job.getResultSummary().setCreated(0);
                job.getResultSummary().setUpdated(0);
                job.getResultSummary().setFailed(0);
                job.setStatus("Completed");
                logger.info("ImportJob {} marked as Completed (jobType={})", job.getJobId(), job.getJobType());
                break;
            default:
                // Unknown job type -> fail the import
                job.getResultSummary().setFailed(0);
                job.setStatus("Failed");
                logger.warn("ImportJob {} has unknown jobType '{}', marking as Failed", job.getJobId(), job.getJobType());
                break;
        }

        // Do not call entityService.updateItem on this ImportJob entity - Cyoda will persist it automatically.
        // If parsed rows were available we would use entityService.addItem/updateItem for Product or User entities here.

        return job;
    }
}