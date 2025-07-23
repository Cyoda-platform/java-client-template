package com.java_template.application.processor;

import com.java_template.application.entity.HNItem;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Component
public class HNItemProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public HNItemProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        logger.info("HNItemProcessor initialized with SerializerFactory and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(HNItem.class)
                .validate(HNItem::isValid, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "HNItemProcessor".equals(modelSpec.operationName()) &&
               "hnItem".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private HNItem processEntityLogic(HNItem entity) {
        logger.info("Processing HNItem with technicalId: {}", entity.getTechnicalId());

        try {
            Map<?, ?> contentMap = objectMapper.readValue(entity.getContent(), Map.class);
            Object contentId = contentMap.get("id");
            Object contentType = contentMap.get("type");
            if (contentId == null || contentType == null || contentId.toString().isBlank() || contentType.toString().isBlank()) {
                entity.setStatus("INVALID");
                logger.info("HNItem technicalId {} validation failed - missing 'id' or 'type'", entity.getTechnicalId());
            } else {
                entity.setStatus("VALIDATED");
                logger.info("HNItem technicalId {} validation succeeded - status set to VALIDATED", entity.getTechnicalId());
            }
        } catch (Exception e) {
            entity.setStatus("INVALID");
            logger.error("Failed to parse content JSON for HNItem technicalId {}", entity.getTechnicalId(), e);
        }
        return entity;
    }
}
