package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
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

import java.util.UUID;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    private final com.java_template.common.service.EntityService entityService;

    public PetJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(PetJob::isValid, "Invalid entity state")
            .map(this::processPetJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
                "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processPetJobLogic(PetJob petJob) {
        logger.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());

        try {
            switch (petJob.getAction().toUpperCase()) {
                case "CREATE":
                    Pet newPet = new Pet();
                    newPet.setId(UUID.randomUUID().toString());
                    newPet.setTechnicalId(UUID.randomUUID());
                    newPet.setPetId(petJob.getPetId());
                    newPet.setName("New Pet");
                    newPet.setCategory("Unknown");
                    newPet.setStatus("AVAILABLE");
                    entityService.addItem("Pet", Config.ENTITY_VERSION, newPet).join();
                    petJob.setStatus("COMPLETED");
                    break;
                case "UPDATE":
                    petJob.setStatus("COMPLETED");
                    break;
                case "DELETE":
                    petJob.setStatus("COMPLETED");
                    break;
                default:
                    logger.error("Unsupported action: {}", petJob.getAction());
                    petJob.setStatus("FAILED");
                    break;
            }
        } catch (Exception e) {
            logger.error("Exception processing PetJob: {}", e.getMessage());
            petJob.setStatus("FAILED");
        }

        return petJob;
    }
}
