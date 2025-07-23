package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AdoptionRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public AdoptionRequestProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("AdoptionRequestProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(AdoptionRequest.class)
                .validate(AdoptionRequest::isValid, "Invalid AdoptionRequest entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestProcessor".equals(modelSpec.operationName()) &&
                "adoptionRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private AdoptionRequest processEntityLogic(AdoptionRequest entity) {
        // Business logic copied from prototype method adoptionRequestProcessing
        try {
            UUID technicalId = entity.getTechnicalId();
            String petId = entity.getPetId();

            CompletableFuture<com.fasterxml.jackson.databind.node.ObjectNode> adoptionRequestFuture =
                    entityService.getItem("AdoptionRequest", Config.ENTITY_VERSION, technicalId);
            com.fasterxml.jackson.databind.node.ObjectNode adoptionRequestNode = adoptionRequestFuture.get();
            if (adoptionRequestNode == null) {
                logger.error("AdoptionRequest not found during processing: {}", technicalId);
                return entity;
            }

            // Check pet availability
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", petId),
                    Condition.of("$.status", "IEQUALS", "ACTIVE"));

            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> petsFuture =
                    entityService.getItemsByCondition("Pet", Config.ENTITY_VERSION, condition, true);
            com.fasterxml.jackson.databind.node.ArrayNode pets = petsFuture.get();

            if (pets == null || pets.isEmpty()) {
                adoptionRequestNode.put("status", "REJECTED");
                logger.error("Pet not available for AdoptionRequest technicalId: {}", technicalId);
            } else {
                adoptionRequestNode.put("status", "APPROVED");
            }

            entityService.updateItem("AdoptionRequest", Config.ENTITY_VERSION, technicalId, adoptionRequestNode).get();

            logger.info("AdoptionRequest {} status set to {}", technicalId, adoptionRequestNode.get("status").asText());

            // Update entity status property to reflect the change
            entity.setStatus(adoptionRequestNode.get("status").asText());
        } catch (Exception e) {
            logger.error("Error processing AdoptionRequest with technicalId {}: {}", entity.getTechnicalId(), e.getMessage());
        }
        return entity;
    }
}
