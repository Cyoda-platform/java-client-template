package com.java_template.application.processor;

import com.java_template.application.entity.AdoptionRequest;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;

import java.util.concurrent.ExecutionException;

@Component
public class AdoptionRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AdoptionRequestProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("AdoptionRequestProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(AdoptionRequest.class)
                .map(this::processAdoptionRequestLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestProcessor".equals(modelSpec.operationName()) &&
               "adoptionRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private AdoptionRequest processAdoptionRequestLogic(AdoptionRequest request) {
        try {
            logger.info("Processing AdoptionRequest with requestId: {}", request.getRequestId());

            // Build search condition for petId
            com.java_template.common.service.SearchConditionRequest petCondition = com.java_template.common.service.SearchConditionRequest.group("AND",
                    com.java_template.common.service.Condition.of("$.petId", "EQUALS", request.getPetId()));

            // Fetch pets matching condition
            ArrayNode petResults = entityService.getItemsByCondition("pet", Config.ENTITY_VERSION, petCondition).get();

            if (petResults.isEmpty()) {
                request.setStatus("REJECTED");
                logger.error("Adoption request rejected: pet not found {}", request.getPetId());
            } else {
                ObjectNode petNode = (ObjectNode) petResults.get(0);
                Pet pet = entityService.getObjectMapper().treeToValue(petNode, Pet.class);

                if (!"AVAILABLE".equals(pet.getStatus())) {
                    request.setStatus("REJECTED");
                    logger.error("Adoption request rejected: pet not available {}", request.getPetId());
                } else {
                    request.setStatus("APPROVED");
                    pet.setStatus("PENDING");
                    entityService.addItem("pet", Config.ENTITY_VERSION, pet).get();
                    logger.info("Adoption request approved for pet {}", pet.getName());
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error processing adoption request", e);
            // Optionally, you could set a failure status or handle the error differently
        }
        return request;
    }
}
