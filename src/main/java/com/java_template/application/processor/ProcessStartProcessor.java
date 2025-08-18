package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
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
public class ProcessStartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessStartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessStartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem start for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .validate(this::isValidEntity, "Entity missing technical id or originalJson")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        return entity != null && entity.getTechnicalId() != null && entity.getOriginalJson() != null;
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        // Mark as processing and update timestamp
        try {
            entity.setStatus("PROCESSING");
            String now = Instant.now().toString();
            entity.setUpdatedAt(now);

            // Persist minimal metadata so other processors/criteria can discover this item
            InMemoryDataStore.itemsByTechnicalId.put(entity.getTechnicalId(), entity);
            logger.info("ProcessStartProcessor: marked item {} as PROCESSING", entity.getTechnicalId());
        } catch (Exception e) {
            logger.error("Error in ProcessStartProcessor for {}: {}", entity.getTechnicalId(), e.getMessage(), e);
            entity.setStatus("FAILED");
        }
        return entity;
    }
}
