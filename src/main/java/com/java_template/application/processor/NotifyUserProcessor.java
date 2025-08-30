package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

@Component
public class NotifyUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifyUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing notification for request: {}", request.getId());

        // This processor can handle different entity types (User, Pet, AdoptionRequest)
        // We'll try to determine the entity type and process accordingly
        return serializer.withRequest(request)
            .map(this::processNotificationLogic)
            .withErrorHandler((error, payload) -> {
                logger.error("Failed to process notification: {}", error.getMessage(), error);
                return new ErrorInfo("NOTIFICATION_ERROR", "Failed to process notification: " + error.getMessage());
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private com.fasterxml.jackson.databind.JsonNode processNotificationLogic(ProcessorSerializer.ProcessorExecutionContext context) {
        com.fasterxml.jackson.databind.JsonNode payload = context.payload();
        
        // Determine entity type and send appropriate notification
        try {
            // Try to extract as User first
            if (payload.has("username")) {
                User user = serializer.extractEntity(context.request(), User.class);
                sendUserNotification(user, "User account updated successfully");
                logger.info("Sent notification to user: {}", user.getUsername());
            }
            // Try to extract as Pet
            else if (payload.has("breed") && payload.has("type")) {
                Pet pet = serializer.extractEntity(context.request(), Pet.class);
                sendPetNotification(pet, "Pet status updated: " + pet.getStatus());
                logger.info("Sent notification for pet: {} ({})", pet.getName(), pet.getStatus());
            }
            // Try to extract as AdoptionRequest
            else if (payload.has("petId") && payload.has("userId")) {
                AdoptionRequest adoptionRequest = serializer.extractEntity(context.request(), AdoptionRequest.class);
                sendAdoptionRequestNotification(adoptionRequest, "Adoption request status: " + adoptionRequest.getStatus());
                logger.info("Sent notification for adoption request: {} ({})", adoptionRequest.getId(), adoptionRequest.getStatus());
            }
            else {
                logger.warn("Unknown entity type for notification, sending generic notification");
                sendGenericNotification("Entity has been updated");
            }
        } catch (Exception e) {
            logger.error("Error processing notification: {}", e.getMessage(), e);
            sendGenericNotification("System notification: Entity updated");
        }
        
        return payload;
    }

    private void sendUserNotification(User user, String message) {
        // In a real implementation, this would send an email, SMS, or push notification
        logger.info("NOTIFICATION [User: {}]: {}", user.getUsername(), message);
        logger.info("Contact Info: {}", user.getContactInfo());
    }

    private void sendPetNotification(Pet pet, String message) {
        // In a real implementation, this would notify interested parties about pet status changes
        logger.info("NOTIFICATION [Pet: {}]: {}", pet.getName(), message);
        logger.info("Pet Details: {} {} (Age: {})", pet.getBreed(), pet.getType(), pet.getAge());
    }

    private void sendAdoptionRequestNotification(AdoptionRequest request, String message) {
        // In a real implementation, this would notify both the user and relevant staff
        logger.info("NOTIFICATION [Adoption Request: {}]: {}", request.getId(), message);
        logger.info("Request Details: User {} requesting Pet {}", request.getUserId(), request.getPetId());
    }

    private void sendGenericNotification(String message) {
        // Fallback notification method
        logger.info("NOTIFICATION [System]: {}", message);
    }
}
