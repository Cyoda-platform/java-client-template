package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class UpdateAdoptionHistoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateAdoptionHistoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateAdoptionHistoryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing adoption history update for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract user entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract user entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid user state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User entity = context.entity();
        
        // For this processor, we need to determine which pet was adopted
        // In a real scenario, this information might come from the request context
        // or from related entities. For now, we'll simulate this logic.
        
        // Get the current adoption history
        List<String> currentHistory = new ArrayList<>();
        if (entity.getAdoptionHistory() != null) {
            currentHistory.addAll(Arrays.asList(entity.getAdoptionHistory()));
        }
        
        // In a real implementation, we would get the petId from the adoption request
        // For now, we'll simulate adding a pet to the adoption history
        String newPetId = extractPetIdFromContext(context);
        
        if (newPetId != null && !currentHistory.contains(newPetId)) {
            currentHistory.add(newPetId);
            entity.setAdoptionHistory(currentHistory.toArray(new String[0]));
            
            logger.info("Updated adoption history for user: {}. Added pet: {}. Total adoptions: {}", 
                       entity.getUsername(), newPetId, currentHistory.size());
        } else if (newPetId != null) {
            logger.info("Pet {} already in adoption history for user: {}", newPetId, entity.getUsername());
        } else {
            logger.warn("No pet ID found to add to adoption history for user: {}", entity.getUsername());
        }
        
        return entity;
    }

    private String extractPetIdFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        // In a real implementation, this would extract the pet ID from the request context
        // or from related adoption request data. For now, we'll simulate this.
        
        try {
            // Try to get pet ID from request metadata or payload
            // This is a simplified implementation
            com.fasterxml.jackson.databind.JsonNode payload = serializer.extractPayload(context.request());
            
            // Check if there's a petId field in the payload (might be from a related adoption request)
            if (payload.has("petId")) {
                return payload.get("petId").asText();
            }
            
            // Check if there's adoption history with a new entry
            if (payload.has("adoptionHistory")) {
                com.fasterxml.jackson.databind.JsonNode historyNode = payload.get("adoptionHistory");
                if (historyNode.isArray() && historyNode.size() > 0) {
                    // Return the last entry as the newly adopted pet
                    return historyNode.get(historyNode.size() - 1).asText();
                }
            }
            
            // If no specific pet ID is found, we might need to look at related entities
            // For now, return null to indicate no pet ID was found
            logger.debug("No pet ID found in request context for user adoption history update");
            return null;
            
        } catch (Exception e) {
            logger.error("Error extracting pet ID from context: {}", e.getMessage(), e);
            return null;
        }
    }
}
