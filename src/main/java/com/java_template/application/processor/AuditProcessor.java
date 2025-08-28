package com.java_template.application.processor;
import com.java_template.application.entity.pickledger.version_1.PickLedger;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class AuditProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AuditProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PickLedger for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PickLedger.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PickLedger entity) {
        return entity != null && entity.isValid();
    }

    private PickLedger processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PickLedger> context) {
        PickLedger entity = context.entity();

        try {
            // Only process audit if currently in AUDIT_PENDING or unset
            String currentStatus = entity.getAuditStatus();
            if (currentStatus != null && !currentStatus.isBlank() && 
                !currentStatus.equalsIgnoreCase("AUDIT_PENDING")) {
                logger.debug("PickLedger {} already audited with status {}", entity.getId(), currentStatus);
                return entity;
            }

            // Random sampling: audit ~10% of pick ledgers
            double sample = ThreadLocalRandom.current().nextDouble();
            boolean selectedForAudit = sample < 0.10;

            if (!selectedForAudit) {
                logger.debug("PickLedger {} not selected for audit (sample={})", entity.getId(), sample);
                // Leave auditStatus unchanged (remain AUDIT_PENDING) so other processors/timers may pick it up later
                return entity;
            }

            // Selected for audit: decide pass/fail.
            // We'll bias towards passing (e.g., 85% pass), but keep it random.
            double passChance = ThreadLocalRandom.current().nextDouble();
            boolean passed = passChance < 0.85;

            if (passed) {
                entity.setAuditStatus("AUDIT_PASSED");
                logger.info("PickLedger {} audit passed (sample={},passChance={})", entity.getId(), sample, passChance);
            } else {
                entity.setAuditStatus("AUDIT_FAILED");
                logger.info("PickLedger {} audit failed (sample={},passChance={})", entity.getId(), sample, passChance);
            }

            // Assign an auditor id and update timestamp to mark audit moment
            entity.setAuditorId(UUID.randomUUID().toString());
            entity.setTimestamp(Instant.now().toString());

            // Do NOT call entityService.updateItem for the triggering entity.
            // The changed entity will be persisted by Cyoda automatically after processor completes.

        } catch (Exception ex) {
            logger.error("Error while auditing PickLedger {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            // In case of error, leave entity unchanged so it can be retried
        }

        return entity;
    }
}