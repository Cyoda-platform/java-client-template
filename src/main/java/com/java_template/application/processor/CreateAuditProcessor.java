package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.importaudit.version_1.ImportAudit;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateAuditProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateAuditProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateAuditProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HNItem entity) {
        return entity != null && entity.isValid();
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();

        try {
            // Build ImportAudit record
            ImportAudit audit = new ImportAudit();
            audit.setAuditId(UUID.randomUUID().toString());
            audit.setHnId(entity.getId());
            // Use the processor request id as a reference for jobId when no explicit job id is available
            String jobRef = null;
            if (context.request() != null && context.request().getId() != null) {
                jobRef = context.request().getId();
            } else {
                jobRef = UUID.randomUUID().toString();
            }
            audit.setJobId(jobRef);
            // Map HNItem status to audit outcome: STORED -> SUCCESS, otherwise FAILURE
            String status = entity.getStatus();
            String outcome = "FAILURE";
            if (status != null && status.equalsIgnoreCase("STORED")) {
                outcome = "SUCCESS";
            }
            audit.setOutcome(outcome);
            audit.setTimestamp(Instant.now().toString());

            // Populate details map with available information from HNItem
            Map<String, Object> details = new HashMap<>();
            details.put("status", entity.getStatus());
            details.put("importTimestamp", entity.getImportTimestamp());
            details.put("originalJson", entity.getOriginalJson());
            audit.setDetails(details);

            // Persist ImportAudit using EntityService (add operation)
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                ImportAudit.ENTITY_NAME,
                String.valueOf(ImportAudit.ENTITY_VERSION),
                audit
            );
            // Wait for completion to ensure audit was recorded (processor semantics expect audit to exist)
            idFuture.join();
            logger.info("Created ImportAudit for HNItem id={} auditId={}", entity.getId(), audit.getAuditId());
        } catch (Exception ex) {
            logger.error("Failed to create ImportAudit for HNItem id={}: {}", entity != null ? entity.getId() : null, ex.getMessage(), ex);
            // Do not modify the triggering entity via entityService update; adjust entity state if needed.
            // Keep entity unchanged here; Cyoda will persist entity state as required by the workflow.
        }

        return entity;
    }
}