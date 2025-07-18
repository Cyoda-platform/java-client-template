package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
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
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public PetProcessor() {
        logger.info("PetProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request id: {}", request.getId());

        // Extract entity and perform validation
        Pet pet = context.getSerializer().extractEntity(request, Pet.class);

        if (!pet.isValid()) {
            logger.error("Invalid Pet entity state");
            return context.getSerializer().responseBuilder(request)
                .withError("Invalid Pet entity")
                .build();
        }

        // Business logic from prototype is just logging
        logger.info("Processing pet id: {}, name: {}, type: {}, status: {}", pet.getId(), pet.getName(), pet.getType(), pet.getStatus());

        // Return success response with the entity unchanged
        return context.getSerializer().responseBuilder(request)
                .withEntity(pet)
                .build();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
