package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.store.InMemoryDataStore;
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

@Component
public class EnrichItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Enriching HackerNewsItem for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        return entity != null && entity.getTechnicalId() != null;
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        String now = Instant.now().toString();
        try {
            entity.setImportTimestamp(now);
            entity.setUpdatedAt(now);

            JsonNode original = entity.getOriginalJson();
            if (original != null && original.isObject()) {
                ((ObjectNode) original).put("importTimestamp", now);
                entity.setOriginalJson(original);
            }

            // Update in-memory metadata store
            InMemoryDataStore.itemsByTechnicalId.put(entity.getTechnicalId(), entity);
            logger.info("Enriched item {} with importTimestamp {}", entity.getTechnicalId(), now);
        } catch (Exception e) {
            logger.error("Error enriching entity {}: {}", entity.getTechnicalId(), e.getMessage(), e);
            entity.setStatus("FAILED");
        }
        return entity;
    }
}
