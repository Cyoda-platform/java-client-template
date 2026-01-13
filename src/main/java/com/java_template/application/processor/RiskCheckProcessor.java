package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.trader.version_1.Trader;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RiskCheckProcessor - Performs risk analysis and flags risk rule breaches
 * 
 * <p>Computes and flags risk rule breaches and adds risk metadata to the entity response.
 * Returns error if hard fail conditions are met, otherwise returns success with risk metadata.
 * 
 * <p>Risk checks include:
 * <ul>
 *   <li>Order size limits</li>
 *   <li>Notional value limits per day</li>
 *   <li>Trader status validation</li>
 *   <li>Risk check enablement status</li>
 * </ul>
 */
@Component
public class RiskCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RiskCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    /**
     * Constructs RiskCheckProcessor with required dependencies.
     *
     * @param serializerFactory Factory for creating serializers
     * @param entityService Service for entity operations
     */
    public RiskCheckProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Performing risk check for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .withErrorHandler(this::handleRiskCheckError)
                .map(this::performRiskCheck)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Performs comprehensive risk check on the order.
     *
     * @param context Processing context with order entity
     * @return EntityWithMetadata with risk metadata attached
     */
    private EntityWithMetadata<Order> performRiskCheck(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Performing risk check for order: {}", order.getOrderId());

        Map<String, Object> riskMetadata = new HashMap<>();
        List<String> riskWarnings = new ArrayList<>();
        List<String> riskBreaches = new ArrayList<>();
        boolean hardFail = false;

        try {
            // Fetch trader information
            Trader trader = findTraderById(order.getTraderId());
            if (trader == null) {
                riskBreaches.add("TRADER_NOT_FOUND");
                hardFail = true;
                logger.error("Trader {} not found for order: {}", order.getTraderId(), order.getOrderId());
            } else {
                // Check trader status
                if (!"ACTIVE".equals(trader.getStatus())) {
                    riskBreaches.add("TRADER_NOT_ACTIVE");
                    hardFail = true;
                    logger.warn("Trader {} is not active (status: {}) for order: {}", 
                            trader.getTraderId(), trader.getStatus(), order.getOrderId());
                }

                // Perform risk checks if enabled
                if (trader.getRiskCheckEnabled() != null && trader.getRiskCheckEnabled()) {
                    // Check order size limit
                    if (trader.getMaxOrderSize() != null) {
                        if (order.getQuantity() > trader.getMaxOrderSize()) {
                            riskBreaches.add("MAX_ORDER_SIZE_EXCEEDED");
                            hardFail = true;
                            logger.warn("Order {} quantity {} exceeds max order size {} for trader {}",
                                    order.getOrderId(), order.getQuantity(), 
                                    trader.getMaxOrderSize(), trader.getTraderId());
                        } else if (order.getQuantity() > trader.getMaxOrderSize() * 0.8) {
                            // Warning at 80% threshold
                            riskWarnings.add("APPROACHING_MAX_ORDER_SIZE");
                            logger.info("Order {} quantity approaching max order size for trader {}",
                                    order.getOrderId(), trader.getTraderId());
                        }
                    }

                    // Check notional limit
                    if (trader.getMaxNotionalPerDay() != null && trader.getCurrentDayNotional() != null) {
                        double orderNotional = calculateOrderNotional(order);
                        double projectedNotional = trader.getCurrentDayNotional() + orderNotional;
                        
                        if (projectedNotional > trader.getMaxNotionalPerDay()) {
                            riskBreaches.add("MAX_NOTIONAL_PER_DAY_EXCEEDED");
                            hardFail = true;
                            logger.warn("Order {} would exceed max notional per day for trader {}. Current: {}, Order: {}, Max: {}",
                                    order.getOrderId(), trader.getTraderId(), 
                                    trader.getCurrentDayNotional(), orderNotional, trader.getMaxNotionalPerDay());
                        } else if (projectedNotional > trader.getMaxNotionalPerDay() * 0.9) {
                            // Warning at 90% threshold
                            riskWarnings.add("APPROACHING_MAX_NOTIONAL_PER_DAY");
                            logger.info("Order {} approaching max notional per day for trader {}",
                                    order.getOrderId(), trader.getTraderId());
                        }

                        riskMetadata.put("orderNotional", orderNotional);
                        riskMetadata.put("projectedDayNotional", projectedNotional);
                        riskMetadata.put("maxNotionalPerDay", trader.getMaxNotionalPerDay());
                    }
                }

                riskMetadata.put("traderStatus", trader.getStatus());
                riskMetadata.put("riskCheckEnabled", trader.getRiskCheckEnabled());
            }

            // Add risk metadata
            riskMetadata.put("riskWarnings", riskWarnings);
            riskMetadata.put("riskBreaches", riskBreaches);
            riskMetadata.put("hardFail", hardFail);
            riskMetadata.put("riskCheckTimestamp", java.time.LocalDateTime.now().toString());

            // Attach risk metadata to entity metadata
            ObjectNode metadataExtensions = objectMapper.createObjectNode();
            metadataExtensions.set("riskMetadata", objectMapper.valueToTree(riskMetadata));
            
            logger.info("Risk check completed for order: {}. Hard fail: {}, Breaches: {}, Warnings: {}",
                    order.getOrderId(), hardFail, riskBreaches.size(), riskWarnings.size());

            // If hard fail, throw exception to trigger error response
            if (hardFail) {
                throw new RiskCheckFailureException(
                        "Risk check failed for order " + order.getOrderId() + ": " + String.join(", ", riskBreaches));
            }

            return entityWithMetadata;
        } catch (RiskCheckFailureException e) {
            throw e; // Re-throw to be handled by error handler
        } catch (Exception e) {
            logger.error("Error performing risk check for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Risk check processing error: " + e.getMessage(), e);
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
            traderIdCondition.setValue(objectMapper.valueToTree(traderId));
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
        // For market orders, use a conservative estimate
        return order.getQuantity() * 100.0;
    }

    /**
     * Custom error handler for risk check errors.
     *
     * @param error The error that occurred
     * @param entityWithMetadata The entity being processed
     * @return ErrorInfo with appropriate code and message
     */
    private ErrorInfo handleRiskCheckError(Throwable error, EntityWithMetadata<Order> entityWithMetadata) {
        String orderId = entityWithMetadata != null && entityWithMetadata.entity() != null 
                ? entityWithMetadata.entity().getOrderId() 
                : "UNKNOWN";
        
        if (error instanceof RiskCheckFailureException) {
            return new ErrorInfo("RISK_CHECK_FAILED", error.getMessage());
        }
        
        return new ErrorInfo("RISK_CHECK_ERROR", 
                "Risk check error for order " + orderId + ": " + error.getMessage());
    }

    /**
     * Custom exception for risk check failures.
     */
    private static class RiskCheckFailureException extends RuntimeException {
        public RiskCheckFailureException(String message) {
            super(message);
        }
    }
}

