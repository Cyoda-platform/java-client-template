package com.java_template.application.processor;

import com.java_template.application.entity.PetOrder;
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
public class PetOrderProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetOrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetOrderProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetOrder for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetOrder.class)
            .validate(PetOrder::isValid)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetOrderProcessor".equals(modelSpec.operationName()) &&
               "petOrder".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetOrder processEntityLogic(PetOrder entity) {
        logger.info("Processing PetOrder with technicalId: {}", entity.getTechnicalId());

        // Business logic for processing PetOrder entity from workflow prototype
        if (entity.getPetId() == null) {
            logger.error("Order processing failed: Pet ID is null");
            return entity;
        }

        if (!"COMPLETED".equalsIgnoreCase(entity.getStatus())) {
            logger.error("Order processing failed: Order status is not COMPLETED for technicalId: {}", entity.getTechnicalId());
            return entity;
        }

        logger.info("Order {} completed successfully for pet ID {}", entity.getTechnicalId(), entity.getPetId());

        return entity;
    }
}
