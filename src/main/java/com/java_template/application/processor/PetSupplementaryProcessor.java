package com.java_template.application.processor;

import com.java_template.application.entity.PetSupplementary;
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
import java.util.function.Function;

@Component
public class PetSupplementaryProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetSupplementaryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetSupplementaryProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetSupplementary for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetSupplementary.class)
                .withErrorHandler(this::handlePetSupplementaryError)
                .validate(this::isValidPetSupplementary, "Invalid petSupplementary state")
                .map(this::applySupplementaryLogic)
                .validate(this::businessValidation, "Failed supplementary business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetSupplementaryProcessor".equals(modelSpec.operationName()) &&
               "petSupplementary".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidPetSupplementary(PetSupplementary petSup) {
        return petSup != null && petSup.isValid();
    }

    private ErrorInfo handlePetSupplementaryError(Throwable throwable, PetSupplementary petSup) {
        logger.error("Error processing PetSupplementary", throwable);
        return new ErrorInfo("PetSupplementary_processing_error", throwable.getMessage());
    }

    private PetSupplementary applySupplementaryLogic(PetSupplementary petSup) {
        // Example logic: append suffix to supplementaryInfo
        if (petSup.getSupplementaryInfo() != null) {
            petSup.setSupplementaryInfo(petSup.getSupplementaryInfo() + "_processed");
        }
        return petSup;
    }

    private boolean businessValidation(PetSupplementary petSup) {
        // Example validation: supplementaryInfo must not be empty
        return petSup.getSupplementaryInfo() != null && !petSup.getSupplementaryInfo().isEmpty();
    }
}
