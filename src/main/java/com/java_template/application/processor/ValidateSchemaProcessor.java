package com.java_template.application.processor;

import com.java_template.application.entity.datafeed.version_1.DataFeed;
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

import java.time.Instant;
import java.util.Map;

@Component
public class ValidateSchemaProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSchemaProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateSchemaProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataFeed for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(DataFeed.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataFeed entity) {
        if (entity == null) return false;
        // Require minimal fields to run schema validation: id, url and status must be present
        if (entity.getId() == null || entity.getId().isBlank()) return false;
        if (entity.getUrl() == null || entity.getUrl().isBlank()) return false;
        if (entity.getStatus() == null || entity.getStatus().isBlank()) return false;
        return true;
    }

    private DataFeed processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataFeed> context) {
        DataFeed entity = context.entity();
        if (entity == null) return null;

        logger.info("ValidateSchemaProcessor starting for DataFeed id={}", entity.getId());

        Map<String, String> schema = entity.getSchemaPreview();
        Integer recordCount = entity.getRecordCount();
        String checksum = entity.getLastChecksum();

        boolean hasSchema = schema != null && !schema.isEmpty();
        boolean hasRecords = recordCount != null && recordCount > 0;
        boolean hasChecksum = checksum != null && !checksum.isBlank();

        // Basic validation rules derived from functional requirements:
        // - schemaPreview must be present with at least one column
        // - recordCount must be present and > 0
        // - lastChecksum should be present
        // On success -> set status to VALIDATED
        // On failure -> set status to FAILED
        if (!hasSchema) {
            logger.warn("DataFeed {} validation failed: missing or empty schemaPreview", entity.getId());
            entity.setStatus("FAILED");
            entity.setUpdatedAt(Instant.now().toString());
            // Do not attempt to modify other entities here. Return entity to be persisted by workflow.
            return entity;
        }

        if (!hasRecords) {
            logger.warn("DataFeed {} validation failed: missing or empty recordCount", entity.getId());
            entity.setStatus("FAILED");
            entity.setUpdatedAt(Instant.now().toString());
            return entity;
        }

        if (!hasChecksum) {
            logger.warn("DataFeed {} validation failed: missing checksum", entity.getId());
            entity.setStatus("FAILED");
            entity.setUpdatedAt(Instant.now().toString());
            return entity;
        }

        // Minimal schema sanity check: ensure at least one column and keys/values are non-blank
        boolean schemaOk = true;
        for (Map.Entry<String, String> e : schema.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().isBlank()) {
                schemaOk = false;
                break;
            }
        }
        if (!schemaOk) {
            logger.warn("DataFeed {} validation failed: invalid schemaPreview entries", entity.getId());
            entity.setStatus("FAILED");
            entity.setUpdatedAt(Instant.now().toString());
            return entity;
        }

        // All checks passed -> mark as VALIDATED
        logger.info("DataFeed {} schema validated successfully (records={}, columns={})", entity.getId(), recordCount, schema.size());
        entity.setStatus("VALIDATED");
        entity.setUpdatedAt(Instant.now().toString());

        return entity;
    }
}