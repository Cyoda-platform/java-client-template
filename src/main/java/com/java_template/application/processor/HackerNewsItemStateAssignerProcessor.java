package com.java_template.application.processor;

import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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
public class HackerNewsItemStateAssignerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HackerNewsItemStateAssignerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public HackerNewsItemStateAssignerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
        return entity;
    }
}
