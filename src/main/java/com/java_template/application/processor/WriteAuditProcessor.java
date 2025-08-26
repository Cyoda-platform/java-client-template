package com.java_template.application.processor;
import com.java_template.application.entity.importaudit.version_1.ImportAudit;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class WriteAuditProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WriteAuditProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public WriteAuditProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportAudit for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ImportAudit.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportAudit entity) {
        // allow processing even when some audit fields are missing so processor can populate them
        return entity != null;
    }

    private ImportAudit processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportAudit> context) {
        ImportAudit entity = context.entity();

        // Ensure we have an auditId
        if (entity.getAuditId() == null || entity.getAuditId().isBlank()) {
            entity.setAuditId(UUID.randomUUID().toString());
        }

        // Normalize details: if empty map -> set to null (ImportAudit.isValid treats empty map as invalid)
        Map<String, Object> details = entity.getDetails();
        if (details != null && details.isEmpty()) {
            entity.setDetails(null);
            details = null;
        }

        // Ensure outcome is set to a reasonable default if missing.
        // Prefer keeping provided outcome, otherwise infer: if details present -> FAILURE, else SUCCESS.
        if (entity.getOutcome() == null || entity.getOutcome().isBlank()) {
            if (details != null) {
                entity.setOutcome("FAILURE");
            } else {
                entity.setOutcome("SUCCESS");
            }
        } else {
            // Normalize outcome to uppercase
            entity.setOutcome(entity.getOutcome().trim().toUpperCase());
        }

        // Ensure jobId is present; set a placeholder if missing to satisfy validity rules.
        if (entity.getJobId() == null || entity.getJobId().isBlank()) {
            entity.setJobId("unknown");
        }

        // Ensure timestamp is set to current UTC ISO-8601 if missing
        if (entity.getTimestamp() == null || entity.getTimestamp().isBlank()) {
            entity.setTimestamp(Instant.now().toString());
        }

        // hnId must be present; do not alter it here (it's expected to be provided by creator).
        // The entity will be persisted automatically by Cyoda after this processor completes.

        logger.info("WriteAuditProcessor populated auditId={}, hnId={}, jobId={}, outcome={}",
                entity.getAuditId(), entity.getHnId(), entity.getJobId(), entity.getOutcome());

        return entity;
    }
}