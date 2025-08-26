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
public class ParseImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ParseImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ParseImportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Business logic:
        // 1. Basic validation of required import fields (jobType and sourceReference).
        // 2. If invalid -> mark job as Failed and populate resultSummary appropriately.
        // 3. If valid -> transition to PERSISTING stage (so PersistEntitiesProcessor will handle actual persistence)
        // 4. Ensure resultSummary is initialized (counts set to zero) so downstream processors can update them.

        if (job == null) {
            logger.warn("ImportJob entity is null in context");
            return job;
        }

        // Initialize result summary if missing
        if (job.getResultSummary() == null) {
            ImportJob.ResultSummary rs = new ImportJob.ResultSummary();
            rs.setCreated(0);
            rs.setUpdated(0);
            rs.setFailed(0);
            job.setResultSummary(rs);
        }

        // Validate jobType
        String jobType = job.getJobType();
        if (jobType == null || jobType.isBlank()) {
            logger.warn("ImportJob.jobType is missing for jobId={}", job.getJobId());
            job.setStatus("Failed");
            // mark as failed (no rows parsed)
            job.getResultSummary().setFailed(
                Math.max(1, job.getResultSummary().getFailed() == null ? 1 : job.getResultSummary().getFailed())
            );
            return job;
        }

        String jt = jobType.trim().toLowerCase();
        if (!("products".equals(jt) || "users".equals(jt))) {
            logger.warn("ImportJob.jobType '{}' is unsupported for jobId={}", jobType, job.getJobId());
            job.setStatus("Failed");
            job.getResultSummary().setFailed(
                Math.max(1, job.getResultSummary().getFailed() == null ? 1 : job.getResultSummary().getFailed())
            );
            return job;
        }

        // Validate sourceReference
        String src = job.getSourceReference();
        if (src == null || src.isBlank()) {
            logger.warn("ImportJob.sourceReference is missing for jobId={}", job.getJobId());
            job.setStatus("Failed");
            job.getResultSummary().setFailed(
                Math.max(1, job.getResultSummary().getFailed() == null ? 1 : job.getResultSummary().getFailed())
            );
            return job;
        }

        // At this stage we treat parsing as successful (actual parsing and persistence happens in PersistEntitiesProcessor).
        // Set status to indicate next step and ensure resultSummary counters are initialized to zero.
        job.setStatus("PERSISTING");
        if (job.getResultSummary().getCreated() == null) job.getResultSummary().setCreated(0);
        if (job.getResultSummary().getUpdated() == null) job.getResultSummary().setUpdated(0);
        if (job.getResultSummary().getFailed() == null) job.getResultSummary().setFailed(0);

        logger.info("ImportJob parsed successfully (logical parse) for jobId={}, jobType={}, source={}", job.getJobId(), job.getJobType(), job.getSourceReference());
        return job;
    }
}