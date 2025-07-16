package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ErrorInfo;
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

import java.util.function.BiFunction;

@Component
public class PetRejectionProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetRejectionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetRejectionProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet rejection for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .withErrorHandler(this::handlePetError)
                .validate(this::isValidPetRejection, "Invalid pet rejection state")
                .map(this::applyRejection)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetRejectionProcessor".equals(modelSpec.operationName()) &&
               "pet".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidPetRejection(Pet pet) {
        return pet != null && "created".equals(pet.getStatus());
    }

    private Pet applyRejection(Pet pet) {
        pet.setStatus("rejected");
        return pet;
    }

    private ErrorInfo handlePetError(Throwable throwable, Pet pet) {
        logger.error("Error processing Pet rejection", throwable);
        return new ErrorInfo("PetRejectionError", throwable.getMessage());
    }
}