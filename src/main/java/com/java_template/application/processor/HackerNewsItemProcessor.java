package com.java_template.application.processor;

import com.java_template.application.entity.HackerNewsItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HackerNewsItemProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public HackerNewsItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer(); //always follow this pattern
        logger.info("HackerNewsItemProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .validate(HackerNewsItem::isValid, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "HackerNewsItemProcessor".equals(modelSpec.operationName()) &&
               "hackerNewsItem".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private HackerNewsItem processEntityLogic(HackerNewsItem entity) {
        logger.info("Processing HackerNewsItem with ID: {}", entity.getId());

        // Validation: check mandatory fields
        if (entity.getId() == null || entity.getId().isBlank() ||
                entity.getType() == null || entity.getType().isBlank()) {
            entity.setState("INVALID");
            logger.info("HackerNewsItem {} marked INVALID due to missing mandatory fields", entity.getId());
        } else {
            entity.setState("VALID");
            logger.info("HackerNewsItem {} marked VALID", entity.getId());
        }

        // Further processing could be added here (mocked)
        return entity;
    }
}
