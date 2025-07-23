package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
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

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(PetJob::isValid, "Invalid entity state")
            .map(this::processPetJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processPetJobLogic(PetJob petJob) {
        logger.info("Processing PetJob with ID: {}", petJob.getId());

        if ("AddPet".equalsIgnoreCase(petJob.getJobType())) {
            logger.info("PetJob is AddPet job, triggering pet creation workflow for petId: {}", petJob.getPetId());
        } else if ("UpdateStatus".equalsIgnoreCase(petJob.getJobType())) {
            logger.info("PetJob is UpdateStatus job for petId: {}", petJob.getPetId());
        } else {
            logger.warn("PetJob has unknown jobType: {}", petJob.getJobType());
        }
        return petJob;
    }
}
