package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.entity.importevent.version_1.ImportEvent;
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
import java.util.UUID;

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
        logger.info("Persisting HackerNewsItem for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .validate(this::isValidEntity, "Invalid entity for persistence")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        return entity != null && entity.getTechnicalId() != null && entity.getId() != null;
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        String now = Instant.now().toString();
        try {
            // Default duplicate policy: OVERWRITE
            JsonNode original = entity.getOriginalJson();
            Long hnId = entity.getId();

            // Simulate write to primary datastore keyed by HN id
            InMemoryDataStore.itemsByHnId.put(hnId, original);

            // Update item metadata and timestamps
            entity.setStatus("READY");
            entity.setUpdatedAt(now);
            InMemoryDataStore.itemsByTechnicalId.put(entity.getTechnicalId(), entity);

            // Emit import event
            ImportEvent ev = new ImportEvent();
            ev.setEventId("event-" + UUID.randomUUID());
            ev.setJobTechnicalId(entity.getSourceJobTechnicalId());
            ev.setItemTechnicalId(entity.getTechnicalId());
            ev.setItemId(entity.getId());
            ev.setTimestamp(now);
            ev.setStatus("SUCCESS");
            InMemoryDataStore.importEvents.add(ev);

            logger.info("Persisted HackerNewsItem {} (hn id {}). Created import event {}", entity.getTechnicalId(), hnId, ev.getEventId());
        } catch (Exception e) {
            logger.error("Error persisting entity {}: {}", entity.getTechnicalId(), e.getMessage(), e);
            entity.setStatus("FAILED");

            ImportEvent ev = new ImportEvent();
            ev.setEventId("event-" + UUID.randomUUID());
            ev.setJobTechnicalId(entity.getSourceJobTechnicalId());
            ev.setItemTechnicalId(entity.getTechnicalId());
            ev.setItemId(entity.getId());
            ev.setTimestamp(now);
            ev.setStatus("FAILURE");
            ev.getErrors().add(e.getMessage());
            InMemoryDataStore.importEvents.add(ev);
        }
        return entity;
    }
}
