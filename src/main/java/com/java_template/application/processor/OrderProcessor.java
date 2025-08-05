package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Order;
import com.java_template.application.entity.Pet;
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

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderProcessor implements CyodaProcessor {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();
    private final com.java_template.common.service.EntityService entityService;

    public OrderProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        log.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        String technicalId = context.request().getEntityId();
        try {
            // 1. Initial State: Set default status if missing
            if (order.getStatus() == null || order.getStatus().isBlank()) {
                order.setStatus("PLACED");
                log.info("Order {} initial status set to PLACED", technicalId);
            }

            // 2. Validation of key fields already done in isValid()

            // 3. Processing: Check pet availability and reserve pet (simulate)
            log.info("Processing Order {} for petId {}", technicalId, order.getPetId());

            // Retrieve pets by petId field using condition (case sensitive)
            Condition petIdCondition = Condition.of("$.petId", "EQUALS", order.getPetId());
            SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", petIdCondition);
            CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition(Pet.ENTITY_NAME, "1", searchCondition, true);
            ArrayNode pets = petsFuture.get();

            if (pets == null || pets.size() == 0) {
                log.error("Pet {} not found for Order {}", order.getPetId(), technicalId);
                order.setStatus("FAILED");
                return order;
            }

            // Take first pet matching
            ObjectNode petNode = (ObjectNode) pets.get(0);
            Pet pet = Pet.fromObjectNode(petNode);

            if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
                log.error("Pet {} is not available for Order {}", pet.getPetId(), technicalId);
                order.setStatus("FAILED");
                return order;
            }

            pet.setStatus("PENDING");
            log.info("Pet {} status set to PENDING due to Order {}", pet.getPetId(), technicalId);

            // TODO: Update pet entity status in EntityService - no update API available, skipping

            // 4. Shipping: Simulate setting shipDate if missing
            if (order.getShipDate() == null || order.getShipDate().isBlank()) {
                order.setShipDate(new Date().toString());
                log.info("Order {} shipDate set to current date", technicalId);
            }

            // 5. Completion: Mark order as APPROVED
            order.setStatus("APPROVED");
            log.info("Order {} status set to APPROVED", technicalId);

            // TODO: Update order entity in EntityService - no update API available, skipping

            // 6. Notification or downstream events could be triggered here
        } catch (Exception e) {
            log.error("Error processing Order {}", technicalId, e);
        }
        return order;
    }
}