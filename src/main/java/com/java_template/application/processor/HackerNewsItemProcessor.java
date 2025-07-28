package com.java_template.application.processor;

import com.java_template.application.entity.HackerNewsItem;
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

import java.util.ArrayList;

@Component
public class HackerNewsItemProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HackerNewsItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(HackerNewsItem.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        return entity != null && entity.isValid();
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        String technicalId = context.request().getEntityId();

        if (entity.getHnId() == null || entity.getHnId() <= 0) {
            logger.error("Invalid hnId in HackerNewsItem with technicalId: {}", technicalId);
            return entity;
        }
        if (entity.getType() == null || entity.getType().isBlank()) {
            logger.error("Invalid type in HackerNewsItem with technicalId: {}", technicalId);
            return entity;
        }
        if (entity.getTime() == null || entity.getTime() <= 0) {
            logger.error("Invalid time in HackerNewsItem with technicalId: {}", technicalId);
            return entity;
        }
        if (entity.getBy() == null || entity.getBy().isBlank()) {
            logger.error("Invalid author (by) in HackerNewsItem with technicalId: {}", technicalId);
            return entity;
        }

        if (entity.getKids() == null) {
            entity.setKids(new ArrayList<>());
            logger.info("Normalized kids list to empty for HackerNewsItem with technicalId: {}", technicalId);
        }

        logger.info("Processed HackerNewsItem with technicalId: {}", technicalId);

        return entity;
    }
}