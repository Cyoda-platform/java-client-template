package com.java_template.application.processor;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.RequestStatusEnum;
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

import java.util.concurrent.ExecutionException;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(AdoptionRequest.class)
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
                "adoptionRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private AdoptionRequest processEntityLogic(AdoptionRequest request) {
        try {
            logger.info("Processing AdoptionRequest with ID: {}", request.getId());

            Pet pet = entityService.getItem("Pet", Config.ENTITY_VERSION, request.getPetId()).get();
            if (pet == null) {
                logger.error("Pet with ID {} not found for adoption request", request.getPetId());
                request.setStatus(RequestStatusEnum.REJECTED);
                entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).get();
                return request;
            }

            if (pet.getStatus() != PetStatusEnum.AVAILABLE) {
                logger.error("Pet with ID {} is not available for adoption in request", pet.getPetId());
                request.setStatus(RequestStatusEnum.REJECTED);
                entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).get();
                return request;
            }

            request.setStatus(RequestStatusEnum.APPROVED);
            entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, request).get();

            logger.info("AdoptionRequest {} approved", request.getId());

            // Notification logic could be added here

            return request;
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error processing AdoptionRequest", e);
            throw new RuntimeException(e);
        }
    }
}
