package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class PersistItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();

    public PersistItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistItem for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HNItem entity) {
        return entity != null && ("ENRICHED".equalsIgnoreCase(entity.getStatus()) || "VALIDATED".equalsIgnoreCase(entity.getStatus()));
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();
        try {
            // Simulate persistence with basic in-memory semantics via static map in absence of a real repository
            // Look for duplicate by hnId if present
            if (entity.getHnId() != null) {
                // Use a simplistic repository emulation class
                HNItemRepository repo = HNItemRepository.getInstance();
                HNItem existing = repo.findByHnId(entity.getHnId());
                if (existing != null) {
                    // update existing
                    existing.setRawJson(entity.getRawJson());
                    existing.setType(entity.getType());
                    existing.setImportTimestamp(entity.getImportTimestamp());
                    String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    existing.setUpdatedAt(now);
                    existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
                    existing.setStatus("STORED");
                    repo.save(existing);
                    // ensure the incoming entity carries the technicalId
                    entity.setTechnicalId(existing.getTechnicalId());
                    entity.setCreatedAt(existing.getCreatedAt());
                    entity.setUpdatedAt(existing.getUpdatedAt());
                    entity.setVersion(existing.getVersion());
                    entity.setStatus("STORED");
                    logger.info("Updated existing HNItem for hnId {} -> technicalId {}", entity.getHnId(), existing.getTechnicalId());
                    return entity;
                }
            }

            // create new
            String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            if (entity.getTechnicalId() == null) {
                entity.setTechnicalId(UUID.randomUUID().toString());
            }
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entity.setVersion(1);
            entity.setStatus("STORED");

            HNItemRepository repo = HNItemRepository.getInstance();
            repo.save(entity);

            logger.info("Persisted new HNItem with technicalId {}", entity.getTechnicalId());
            return entity;
        } catch (Exception e) {
            logger.error("Error persisting HNItem {}: {}", entity == null ? "<null>" : entity.getTechnicalId(), e.getMessage(), e);
            if (entity != null) {
                entity.setStatus("INVALID");
                entity.setErrorMessage("Persistence error: " + e.getMessage());
            }
            return entity;
        }
    }
}
