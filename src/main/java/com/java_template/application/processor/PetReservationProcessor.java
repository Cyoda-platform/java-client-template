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
 * Processor for reserving pets.
 * Handles the reserve_pet transition from available to pending.
 */
@Component
public class PetReservationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetReservationProcessor.class);
    private final ProcessorSerializer serializer;

    public PetReservationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing pet reservation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .map(context -> {
                Pet pet = context.entity();
                
                // Log reservation event
                logger.info("Reserved pet with ID: {} and name: {}", pet.getId(), pet.getName());
                
                // Pet state will be automatically updated by the workflow
                return pet;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "PetReservationProcessor".equals(opSpec.operationName()) &&
               "Pet".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
