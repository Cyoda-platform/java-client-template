package com.java_template.application.processor;

import com.java_template.application.entity.Order;
import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class OrderProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private static final String PET_MODEL = "Pet";

    public OrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("OrderProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Order.class)
                .validate(this::validateOrder, "Invalid Order state")
                .map(this::processOrderLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "OrderProcessor".equals(modelSpec.operationName()) &&
                "order".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean validateOrder(Order order) {
        return order != null && order.getPetId() != null && !order.getPetId().isBlank() &&
               order.getQuantity() != null && order.getQuantity() > 0 &&
               order.getStatus() != null && !order.getStatus().isBlank();
    }

    private Order processOrderLogic(Order order) {
        try {
            UUID petUuid = UUID.fromString(order.getPetId());
            Pet pet = entityService.getItem(PET_MODEL, Config.ENTITY_VERSION, petUuid).get();
            if (pet == null) {
                logger.error("Order processing failed: Pet not found with ID: {}", order.getPetId());
                order.setStatus("FAILED");
            } else {
                String petStatus = pet.getStatus();
                if (!"AVAILABLE".equalsIgnoreCase(petStatus)) {
                    logger.error("Order processing failed: Pet not available with ID: {}", order.getPetId());
                    order.setStatus("FAILED");
                } else {
                    order.setStatus("APPROVED");
                }
            }
        } catch (Exception e) {
            logger.error("Exception during order processing: {}", e.getMessage());
            order.setStatus("FAILED");
        }
        return order;
    }
}
