package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cartorder.version_1.CartOrder;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class CheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CheckoutProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CartOrder for checkout request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CartOrder.class)
            .validate(this::isValidEntity, "Invalid cart order state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CartOrder entity) {
        return entity != null && entity.isValid();
    }

    private CartOrder processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CartOrder> context) {
        CartOrder order = context.entity();
        try {
            // Basic validations
            if (order.getItems() == null || order.getItems().isEmpty()) {
                logger.warn("Checkout failed - empty cart for orderId={}", order.getOrderId());
                // keep state unchanged; in a real system we might attach warnings
                return order;
            }

            // Optional availability check (non committal) - ensure no requested quantity exceeds known stock
            for (CartOrder.Item item : order.getItems()) {
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    logger.warn("Invalid item quantity for product {} in order {}", item.getProductId(), order.getOrderId());
                    // reject checkout
                    return order;
                }
                // Query product by productId
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.productId", "EQUALS", item.getProductId())
                );
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
                );
                ArrayNode results = itemsFuture.get(5, TimeUnit.SECONDS);
                if (results == null || results.size() == 0) {
                    logger.warn("Product {} not found during checkout for order {}", item.getProductId(), order.getOrderId());
                    return order;
                }
                JsonNode prodNode = results.get(0);
                Product product = objectMapper.treeToValue(prodNode, Product.class);
                if (product.getStockQuantity() == null || product.getStockQuantity() < item.getQuantity()) {
                    logger.warn("Insufficient stock for product {} (requested={}, available={})", item.getProductId(), item.getQuantity(), product.getStockQuantity());
                    return order; // fail checkout due to insufficient stock (non-committal)
                }
            }

            // All good - move to PendingPayment
            order.setStatus("PendingPayment");
            logger.info("Order {} moved to PendingPayment", order.getOrderId());
            return order;
        } catch (Exception e) {
            logger.error("Error processing checkout for order {}: {}", order.getOrderId(), e.getMessage(), e);
            // in case of error, we do not change the order
            return order;
        }
    }
}
