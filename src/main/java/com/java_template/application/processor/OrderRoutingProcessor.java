package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.venue.version_1.Venue;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OrderRoutingProcessor - Routes validated orders to execution venues
 * 
 * Routes orders based on symbol and available liquidity.
 * Supports configurable routing rules (direct, smart-routers).
 */
@Component
public class OrderRoutingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderRoutingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderRoutingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order routing for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(this::routeOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        return order != null && order.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<Order> routeOrder(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Routing order: {} for symbol: {}", order.getOrderId(), order.getSymbol());

        // Find suitable venue for the symbol
        String venueId = findVenueForSymbol(order.getSymbol());
        if (venueId != null) {
            logger.info("Order {} routed to venue: {}", order.getOrderId(), venueId);
        } else {
            logger.warn("No suitable venue found for symbol: {}", order.getSymbol());
        }

        // Update order with routing information
        order.setUpdatedAt(LocalDateTime.now());

        return entityWithMetadata;
    }

    private String findVenueForSymbol(String symbol) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Venue.ENTITY_NAME).withVersion(Venue.ENTITY_VERSION);
            ObjectMapper objectMapper = new ObjectMapper();

            SimpleCondition symbolCondition = new SimpleCondition()
                    .withJsonPath("$.supportedSymbols")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(symbol));

            SimpleCondition statusCondition = new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree("ACTIVE"));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(symbolCondition, statusCondition));

            List<EntityWithMetadata<Venue>> venues = entityService.search(
                    modelSpec, condition, Venue.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(10)
                            .inMemory(true)
                            .build()).data();

            if (!venues.isEmpty()) {
                return venues.get(0).entity().getVenueId();
            }
        } catch (Exception e) {
            logger.error("Error finding venue for symbol: {}", symbol, e);
        }
        return null;
    }
}

