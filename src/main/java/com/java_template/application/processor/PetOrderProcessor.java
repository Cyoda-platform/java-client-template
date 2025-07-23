package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetOrder;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PetOrderProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    // Emulate pet repository cache for processing within this processor
    // In real case, this should be replaced by repository or service calls
    private final Map<String, Pet> petCache = new ConcurrentHashMap<>();

    public PetOrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetOrderProcessor initialized with SerializerFactory");

        // Initialize petCache with empty map or some test data if needed
        // This is a placeholder; real implementation should query actual data store
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetOrder for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetOrder.class)
            .validate(PetOrder::isValid)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetOrderProcessor".equals(modelSpec.operationName()) &&
               "petOrder".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetOrder processEntityLogic(PetOrder order) {
        logger.info("Processing PetOrder with technicalId: {}", order.getTechnicalId());

        // Validate order data and pet availability
        if (order.getPetId() == null || order.getPetId().isBlank()) {
            logger.error("Order processing failed: Pet ID is null or blank");
            order.setStatus("FAILED");
            return order;
        }

        // Attempt to get pet entity from cache
        Pet pet = petCache.get(order.getPetId());
        if (pet == null) {
            logger.error("Order processing failed: Pet not found with ID: {}", order.getPetId());
            order.setStatus("FAILED");
            return order;
        }

        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            logger.error("Order processing failed: Pet with ID {} not available", pet.getPetId());
            order.setStatus("FAILED");
            return order;
        }

        if (order.getQuantity() == null || order.getQuantity() < 1) {
            logger.error("Order processing failed: Invalid quantity for order ID: {}", order.getTechnicalId());
            order.setStatus("FAILED");
            return order;
        }

        // Reserve pet by marking it SOLD
        pet.setStatus("SOLD");
        petCache.put(pet.getPetId(), pet);

        // Update order status to COMPLETED
        order.setStatus("COMPLETED");

        logger.info("Order {} completed successfully for pet ID {}", order.getTechnicalId(), order.getPetId());

        return order;
    }

    // This method can be used externally to set pet cache for processing
    public void setPetCache(Map<String, Pet> petCache) {
        this.petCache.clear();
        if (petCache != null) {
            this.petCache.putAll(petCache);
        }
    }
}
