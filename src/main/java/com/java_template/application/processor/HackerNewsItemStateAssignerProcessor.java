package com.java_template.application.processor;

import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class HackerNewsItemStateAssignerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HackerNewsItemStateAssignerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public HackerNewsItemStateAssignerProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Assigning state for HackerNewsItem for request: {}", request.getId());

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
        return entity != null;
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        boolean hasId = entity.getId() != null;
        boolean hasType = entity.getType() != null && !entity.getType().trim().isEmpty();
        if (hasId && hasType) {
            entity.setState("VALID");
            entity.setValidationErrors(null);
        } else {
            entity.setState("INVALID");
            StringBuilder sb = new StringBuilder();
            if (!hasId) sb.append("missing id");
            if (!hasId && !hasType) sb.append(" and ");
            if (!hasType) sb.append("missing type");
            entity.setValidationErrors(sb.toString());
        }

        // Persist state update to datastore if technicalId available
        try {
            if (entity.getCreatedAt() != null) {
                // Try to find the stored item by matching originalJson
                // This is a best-effort attempt; in production prefer technicalId mapping
                // No direct technicalId on entity in current model so we try to search by originalJson
            }
            // no-op in this simplified model
        } catch (Exception e) {
            logger.warn("Failed to persist state assignment: {}", e.getMessage());
        }
        return entity;
    }
}
