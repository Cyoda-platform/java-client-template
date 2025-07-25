package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;

@Component
public class HNItemProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public HNItemProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.serializer = serializerFactory.getDefaultProcessorSerializer(); // always follow this pattern
        logger.info("HNItemProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(HNItem.class)
                .validate(this::isValidEntity)
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "HNItemProcessor".equals(modelSpec.operationName()) &&
                "hnItem".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(HNItem entity) {
        // The entity POJO already has isValid() method, we can delegate to it
        return entity != null && entity.isValid();
    }

    // Business logic from processHNItem method in the prototype
    private HNItem processEntityLogic(HNItem entity) {
        logger.info("Processing HNItem with technicalId: {}", entity.getTechnicalId());

        try {
            Map<String, Object> payloadMap = objectMapper.readValue(entity.getPayload(), Map.class);
            boolean hasType = payloadMap.containsKey("type") && payloadMap.get("type") != null && !payloadMap.get("type").toString().isBlank();
            boolean hasId = payloadMap.containsKey("id") && payloadMap.get("id") != null && !payloadMap.get("id").toString().isBlank();

            if (hasType && hasId) {
                // Create new immutable HNItem version with status VALIDATED
                HNItem validatedItem = new HNItem();
                validatedItem.setTechnicalId(entity.getTechnicalId());
                validatedItem.setId(entity.getId());
                validatedItem.setPayload(entity.getPayload());
                validatedItem.setCreatedAt(entity.getCreatedAt());
                validatedItem.setStatus("VALIDATED");

                // The prototype mentions no update method in EntityService, so we skip update.
                // Changes to current entity passed by reference are not persisted automatically.
                // So we update the current entity reference directly.

                entity.setStatus("VALIDATED");

                logger.info("HNItem with technicalId {} validated successfully", entity.getTechnicalId());
            } else {
                logger.info("HNItem with technicalId {} remains INVALID due to missing required fields", entity.getTechnicalId());
            }
        } catch (Exception e) {
            logger.error("Failed to process validation for HNItem with technicalId: {}", entity.getTechnicalId(), e);
        }
        return entity;
    }
}
