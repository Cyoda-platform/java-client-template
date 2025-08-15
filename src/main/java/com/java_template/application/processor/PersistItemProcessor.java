package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final EntityService entityService;

    public PersistItemProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
            // Duplicate handling by hnId via entityService: try to find existing by hnId
            if (entity.getId() != null) {
                try {
                    // search via entityService.getItemsByCondition
                    com.fasterxml.jackson.databind.node.ArrayNode items = entityService.getItemsByCondition(
                        HNItem.ENTITY_NAME,
                        String.valueOf(HNItem.ENTITY_VERSION),
                        com.java_template.common.util.SearchConditionRequest.group(
                            com.java_template.common.util.Condition.of("$.id", "EQUALS", String.valueOf(entity.getId()))
                        ),
                        true
                    ).join();

                    if (items != null && items.size() > 0) {
                        // take first as existing
                        com.fasterxml.jackson.databind.JsonNode existingNode = items.get(0);
                        HNItem existing = mapper.treeToValue(existingNode, HNItem.class);
                        // update existing fields in-memory (do NOT call entityService.updateItem per rules)
                        existing.setRawJson(entity.getRawJson());
                        existing.setType(entity.getType());
                        existing.setImportTimestamp(entity.getImportTimestamp());
                        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        existing.setUpdatedAt(now);
                        existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
                        existing.setStatus("STORED");

                        // persist using entityService.addItem to record a new entity version externally if desired
                        try {
                            CompletableFuture<java.util.UUID> fut = entityService.addItem(
                                HNItem.ENTITY_NAME,
                                String.valueOf(HNItem.ENTITY_VERSION),
                                existing
                            );
                            java.util.UUID id = fut.join();
                            if (id != null) {
                                existing.setTechnicalId(id.toString());
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to persist updated HNItem via EntityService: {}", e.getMessage());
                        }

                        // reflect technical id back to the incoming entity
                        entity.setTechnicalId(existing.getTechnicalId());
                        entity.setCreatedAt(existing.getCreatedAt());
                        entity.setUpdatedAt(existing.getUpdatedAt());
                        entity.setVersion(existing.getVersion());
                        entity.setStatus("STORED");

                        // Also save in-memory for demo
                        HNItemRepository.getInstance().save(existing);

                        logger.info("Updated existing HNItem for id {} -> technicalId {}", entity.getId(), existing.getTechnicalId());
                        return entity;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to query existing HNItem via EntityService: {}", e.getMessage());
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

            try {
                CompletableFuture<java.util.UUID> fut = entityService.addItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    entity
                );
                java.util.UUID id = fut.join();
                if (id != null) {
                    entity.setTechnicalId(id.toString());
                }
            } catch (Exception e) {
                logger.warn("Failed to persist new HNItem via EntityService: {}", e.getMessage());
            }

            HNItemRepository.getInstance().save(entity);

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
