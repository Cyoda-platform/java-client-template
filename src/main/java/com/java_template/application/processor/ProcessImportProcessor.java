package com.java_template.application.processor;

import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.store.InMemoryHnItemStore;
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
import java.time.temporal.ChronoUnit;

@Component
public class ProcessImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final InMemoryHnItemStore store;

    public ProcessImportProcessor(SerializerFactory serializerFactory, InMemoryHnItemStore store) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.store = store;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing import for HackerNewsItem request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        if (entity == null) return null;
        // Ensure importTimestamp is set or updated
        String importTs = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        try {
            entity.setImportTimestamp(importTs);
        } catch (Exception e) {
            logger.warn("Failed to set importTimestamp on entity: {}", e.getMessage());
        }

        // Persist
        store.upsert(entity);
        logger.info("Processed import for id={}", entity.getId());
        return entity;
    }
}
