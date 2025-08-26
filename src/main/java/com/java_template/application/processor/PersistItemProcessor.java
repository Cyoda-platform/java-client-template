package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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
public class PersistItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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

    /**
     * Validate entity using accessors instead of reflection.
     */
    private boolean isValidEntity(HNItem entity) {
        if (entity == null) return false;

        if (entity.getId() == null || entity.getId() <= 0) return false;

        String type = entity.getType();
        if (type == null || type.isBlank()) return false;

        String originalJson = entity.getOriginalJson();
        if (originalJson == null || originalJson.isBlank()) return false;

        String importTimestamp = entity.getImportTimestamp();
        if (importTimestamp == null || importTimestamp.isBlank()) return false;

        // status is allowed to be set by processors; ensure it's present but allow processor to set it if needed
        String status = entity.getStatus();
        if (status == null || status.isBlank()) {
            // not strictly invalid here; PersistItemProcessor will mark it appropriately.
            // But for safety, allow entities without status to pass validation stage for this processor.
            // Return true as other required fields are present.
        }

        return true;
    }

    /**
     * Main business logic for persisting a HNItem.
     * Uses direct accessors instead of reflection.
     */
    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();

        if (entity == null) {
            logger.warn("Received null HNItem in PersistItemProcessor");
            return null;
        }

        Long hnId = entity.getId();
        String hnIdStr = hnId != null ? String.valueOf(hnId) : "unknown";

        try {
            // Ensure required persisted data exists before marking as STORED.
            String originalJson = entity.getOriginalJson();
            if (originalJson == null || originalJson.isBlank()) {
                logger.warn("HNItem {} missing originalJson, marking as FAILED", hnIdStr);
                entity.setStatus("FAILED");
                return entity;
            }

            String importTimestamp = entity.getImportTimestamp();
            if (importTimestamp == null || importTimestamp.isBlank()) {
                logger.warn("HNItem {} missing importTimestamp, marking as FAILED", hnIdStr);
                entity.setStatus("FAILED");
                return entity;
            }

            // Business action: mark the HN item as persisted/stored.
            // Per rules: do not call entityService.update on the triggering entity.
            // Changing the entity state is sufficient; Cyoda will persist it.
            entity.setStatus("STORED");
            logger.info("HNItem {} marked as STORED", hnIdStr);
        } catch (Exception ex) {
            logger.error("Error while processing HNItem {}: {}", hnIdStr, ex.getMessage(), ex);
            try {
                entity.setStatus("FAILED");
            } catch (Exception ignore) {
            }
        }

        return entity;
    }
}