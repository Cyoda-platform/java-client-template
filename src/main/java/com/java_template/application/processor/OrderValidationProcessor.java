package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.trader.version_1.Trader;
import com.java_template.application.entity.venue.version_1.Venue;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * OrderValidationProcessor - Validates orders before processing
 * 
 * <p>Performs comprehensive validation including:
 * <ul>
 *   <li>Mandatory field validation (orderId, symbol, side, quantity)</li>
 *   <li>Positive quantity validation</li>
 *   <li>Supported symbol check using Venue.supportedSymbols</li>
 *   <li>Basic risk checks (trader.maxOrderSize, trader.currentDayNotional vs maxNotionalPerDay)</li>
 * </ul>
 * 
 * <p>On validation failure, returns an EntityProcessorCalculationResponse with error code and message.
 * On success, returns success with the entity unchanged.
 */
@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    /**
     * Constructs OrderValidationProcessor with required dependencies.
     *
     * @param serializerFactory Factory for creating serializers
     * @param entityService Service for entity operations
     */
    public OrderValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating Order for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .withErrorHandler(this::handleValidationError)
                .validate(this::validateMandatoryFields, "Mandatory field validation failed")
                .validate(this::validateSymbolSupported, "Symbol validation failed")
                .validate(this::validateRiskLimits, "Risk limit validation failed")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates mandatory fields of the order.
     *
     * @param entityWithMetadata Order entity with metadata
     * @return true if all mandatory fields are valid
     */
    private boolean validateMandatoryFields(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        
        if (order == null) {
            logger.warn("Order is null");
            return false;
        }

        // Validate using entity's isValid method
        if (!order.isValid(entityWithMetadata.metadata())) {
            logger.warn("Order validation failed for orderId: {}", order.getOrderId());
            return false;
        }

        // Additional validation for positive quantity
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            logger.warn("Order {} has invalid quantity: {}", order.getOrderId(), order.getQuantity());
            return false;
        }

        logger.debug("Mandatory field validation passed for order: {}", order.getOrderId());
        return true;
    }

    /**
     * Validates that the order symbol is supported by at least one venue.
     *
     * @param entityWithMetadata Order entity with metadata
     * @return true if symbol is supported
     */
    private boolean validateSymbolSupported(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        String symbol = order.getSymbol();

        try {
            ModelSpec venueModelSpec = new ModelSpec();
            venueModelSpec.setName(Venue.ENTITY_NAME);
            venueModelSpec.setVersion(Venue.ENTITY_VERSION);

            // Search for venues that support this symbol
            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition symbolCondition = new SimpleCondition();
            symbolCondition.setJsonPath("$.supportedSymbols[*]");
            symbolCondition.setOperation(Operation.EQUALS);
            symbolCondition.setValue(serializer.getObjectMapper().valueToTree(symbol));
            conditions.add(symbolCondition);

            GroupCondition groupCondition = new GroupCondition();
            groupCondition.setOperator(GroupCondition.Operator.AND);
            groupCondition.setConditions(conditions);

            List<EntityWithMetadata<Venue>> venues = entityService.search(
                    venueModelSpec, groupCondition, Venue.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (venues.isEmpty()) {
                logger.warn("Symbol {} is not supported by any venue for order: {}", symbol, order.getOrderId());
                return false;
            }

            logger.debug("Symbol {} is supported for order: {}", symbol, order.getOrderId());
            return true;
        } catch (Exception e) {
            logger.error("Error validating symbol support for order: {}", order.getOrderId(), e);
            return false;
        }
    }

    /**
     * Validates risk limits for the order.
     *
     * @param entityWithMetadata Order entity with metadata
     * @return true if risk limits are satisfied
     */
    private boolean validateRiskLimits(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        String traderId = order.getTraderId();

        try {
            // Fetch trader information
            Trader trader = findTraderById(traderId);
            if (trader == null) {
                logger.warn("Trader {} not found for order: {}", traderId, order.getOrderId());
                return false;
            }

            // Check if risk checks are enabled
            if (trader.getRiskCheckEnabled() == null || !trader.getRiskCheckEnabled()) {
                logger.debug("Risk checks disabled for trader: {}", traderId);
                return true;
            }

            // Validate max order size
            if (trader.getMaxOrderSize() != null && order.getQuantity() > trader.getMaxOrderSize()) {
                logger.warn("Order {} quantity {} exceeds trader {} max order size {}",
                        order.getOrderId(), order.getQuantity(), traderId, trader.getMaxOrderSize());
                return false;
            }

            // Validate max notional per day
            if (trader.getMaxNotionalPerDay() != null && trader.getCurrentDayNotional() != null) {
                double orderNotional = calculateOrderNotional(order);
                double projectedNotional = trader.getCurrentDayNotional() + orderNotional;
                
                if (projectedNotional > trader.getMaxNotionalPerDay()) {
                    logger.warn("Order {} would exceed trader {} max notional per day. Current: {}, Order: {}, Max: {}",
                            order.getOrderId(), traderId, trader.getCurrentDayNotional(), 
                            orderNotional, trader.getMaxNotionalPerDay());
                    return false;
                }
            }

            logger.debug("Risk limit validation passed for order: {}", order.getOrderId());
            return true;
        } catch (Exception e) {
            logger.error("Error validating risk limits for order: {}", order.getOrderId(), e);
            return false;
        }
    }

    /**
     * Finds a trader by trader ID.
     *
     * @param traderId Trader business ID
     * @return Trader entity or null if not found
     */
    private Trader findTraderById(String traderId) {
        try {
            ModelSpec traderModelSpec = new ModelSpec();
            traderModelSpec.setName(Trader.ENTITY_NAME);
            traderModelSpec.setVersion(Trader.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition traderIdCondition = new SimpleCondition();
            traderIdCondition.setJsonPath("$.traderId");
            traderIdCondition.setOperation(Operation.EQUALS);
            traderIdCondition.setValue(serializer.getObjectMapper().valueToTree(traderId));
            conditions.add(traderIdCondition);

            GroupCondition groupCondition = new GroupCondition();
            groupCondition.setOperator(GroupCondition.Operator.AND);
            groupCondition.setConditions(conditions);

            List<EntityWithMetadata<Trader>> traders = entityService.search(
                    traderModelSpec, groupCondition, Trader.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            return traders.isEmpty() ? null : traders.get(0).entity();
        } catch (Exception e) {
            logger.error("Error finding trader: {}", traderId, e);
            return null;
        }
    }

    /**
     * Calculates the notional value of an order.
     *
     * @param order Order entity
     * @return Notional value
     */
    private double calculateOrderNotional(Order order) {
        if (order.getPrice() != null) {
            return order.getQuantity() * order.getPrice();
        }
        // For market orders, use a conservative estimate or default
        // In production, this might use current market price
        return order.getQuantity() * 100.0; // Conservative estimate
    }

    /**
     * Custom error handler for validation errors.
     *
     * @param error The error that occurred
     * @param entityWithMetadata The entity being processed
     * @return ErrorInfo with appropriate code and message
     */
    private ErrorInfo handleValidationError(Throwable error, EntityWithMetadata<Order> entityWithMetadata) {
        String orderId = entityWithMetadata != null && entityWithMetadata.entity() != null 
                ? entityWithMetadata.entity().getOrderId() 
                : "UNKNOWN";
        return new ErrorInfo("ORDER_VALIDATION_ERROR", 
                "Validation failed for order " + orderId + ": " + error.getMessage());
    }
}

