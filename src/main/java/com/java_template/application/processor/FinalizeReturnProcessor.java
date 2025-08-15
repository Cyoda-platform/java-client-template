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

import java.time.Instant;

@Component
public class FinalizeReturnProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeReturnProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FinalizeReturnProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FinalizeReturn for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet pet) {
        return pet != null && pet.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            if (!"returned".equalsIgnoreCase(pet.getStatus())) {
                logger.warn("Cannot finalize return for pet {} when status is {}", pet.getId(), pet.getStatus());
                return pet;
            }

            // Perform simple validation: if tags indicate damaged, keep returned; otherwise mark available
            boolean damaged = pet.getTags().stream().anyMatch(t -> t.toLowerCase().contains("damaged"));
            if (damaged) {
                // keep in returned for manual inspection
                pet.getTags().add("return:needs_inspection");
                logger.info("Pet {} return requires inspection, leaving in returned state", pet.getId());
                pet.setUpdatedAt(Instant.now().toString());
                return pet;
            }

            // finalize to available
            pet.setStatus("available");
            pet.setRequestedBy(null);
            pet.setAdoptedBy(null);
            pet.setAdoptedAt(null);
            pet.setUpdatedAt(Instant.now().toString());
            pet.getTags().add("return:finalized");
            logger.info("Pet {} return finalized and set to available", pet.getId());
        } catch (Exception e) {
            logger.error("Error in FinalizeReturnProcessor for pet {}: {}", pet.getId(), e.getMessage(), e);
        }
        return pet;
    }
}
