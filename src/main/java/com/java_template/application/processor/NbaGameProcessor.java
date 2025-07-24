package com.java_template.application.processor;

import com.java_template.application.entity.NbaGame;
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
public class NbaGameProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public NbaGameProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("NbaGameProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NbaGame for request: {}", request.getId());

        // Here is an example of possible processing logic based on the entity fields and workflow context
        // For this example, let's assume the processor just validates and sets a status if not set

        return serializer.withRequest(request)
            .toEntity(NbaGame.class)
            .validate(NbaGame::isValid, "Invalid NbaGame entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NbaGameProcessor".equals(modelSpec.operationName()) &&
               "nbaGame".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private NbaGame processEntityLogic(NbaGame entity) {
        // Business logic for NbaGame entity processing
        // Example: If status is null or blank, set to "REPORTED"
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("REPORTED");
        }
        // Additional logic could be added here as needed
        return entity;
    }
}
