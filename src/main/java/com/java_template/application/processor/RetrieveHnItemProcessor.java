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

import java.util.Optional;

@Component
public class RetrieveHnItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetrieveHnItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final InMemoryHnItemStore store;

    public RetrieveHnItemProcessor(SerializerFactory serializerFactory, InMemoryHnItemStore store) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.store = store;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem retrieve for request: {}", request.getId());

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
        Integer id = entity != null ? entity.getId() : null;
        if (id == null) {
            logger.warn("Retrieve called with null id");
            return entity;
        }
        Optional<HackerNewsItem> stored = store.get(id);
        if (stored.isPresent()) {
            HackerNewsItem item = stored.get();
            // Return stored item as the result of processing
            return item;
        } else {
            // No stored item - log and return original entity (higher layers should handle not found)
            logger.info("HackerNewsItem id={} not found in store", id);
            return entity;
        }
    }
}
