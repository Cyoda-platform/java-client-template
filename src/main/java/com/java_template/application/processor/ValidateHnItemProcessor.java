package com.java_template.application.processor;

import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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

@Component
public class ValidateHnItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateHnItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateHnItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing validation for HackerNewsItem request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .validate(this::isValidEntity, "HackerNewsItem validation failed")
            .map(ctx -> ctx.entity())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        if (entity == null) return false;
        String idStr = entity.getId();
        String type = entity.getType();
        if (idStr == null) return false;
        try {
            long id = Long.parseLong(idStr);
            if (id < 0) return false;
        } catch (NumberFormatException e) {
            return false;
        }
        if (type == null || type.trim().isEmpty()) return false;
        return true;
    }
}
