package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
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

/**
 * Processor for selling pets.
 * Handles both sell_pet (pending → sold) and direct_sale (available → sold) transitions.
 */
@Component
public class PetSaleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetSaleProcessor.class);
    private final ProcessorSerializer serializer;

    public PetSaleProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing pet sale for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .map(context -> {
                Pet pet = context.entity();
                
                // Log sale event
                logger.info("Sold pet with ID: {} and name: {}", pet.getId(), pet.getName());
                
                // Pet state will be automatically updated by the workflow
                // Note: In a real implementation, this might trigger order completion
                // or other business logic, but per the requirements, we cannot update
                // the current entity - only read it and potentially update other entities
                
                return pet;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "PetSaleProcessor".equals(opSpec.operationName()) &&
               "Pet".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
