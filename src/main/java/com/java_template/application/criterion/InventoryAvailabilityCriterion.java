package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.inventory_item.version_1.InventoryItem;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ABOUTME: InventoryAvailabilityCriterion validates that sufficient inventory is available
 * for all order line items before allowing transition from Paid to Packed state.
 */
@Component
public class InventoryAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InventoryAvailabilityCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Inventory availability criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateInventoryAvailability)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateInventoryAvailability(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        // Check if order is null (structural validation)
        if (order == null) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "Order entity is null");
        }

        // Validate line items exist
        if (order.getLineItems() == null || order.getLineItems().isEmpty()) {
            return EvaluationOutcome.fail(StandardEvalReasonCategories.STRUCTURAL_FAILURE, 
                                        "Order has no line items to check inventory for");
        }

        try {
            // Check inventory availability for each line item
            for (Order.LineItem lineItem : order.getLineItems()) {
                EvaluationOutcome itemCheck = checkLineItemInventory(lineItem, order.getOrderId());
                if (!itemCheck.isSuccess()) {
                    return itemCheck; // Return first failure
                }
            }

            logger.debug("Inventory availability validation passed for orderId: {}", order.getOrderId());
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error checking inventory availability for orderId: {}", order.getOrderId(), e);
            return EvaluationOutcome.fail(StandardEvalReasonCategories.EXTERNAL_DEPENDENCY_FAILURE, 
                                        "Failed to check inventory availability: " + e.getMessage());
        }
    }

    private EvaluationOutcome checkLineItemInventory(Order.LineItem lineItem, String orderId) {
        try {
            // Find inventory item by productId
            ModelSpec inventoryModelSpec = new ModelSpec();
            inventoryModelSpec.setName(InventoryItem.ENTITY_NAME);
            inventoryModelSpec.setVersion(InventoryItem.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();
            SimpleCondition productCondition = new SimpleCondition()
                    .withJsonPath("$.productId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(lineItem.getProductId()));
            conditions.add(productCondition);

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<com.java_template.common.dto.EntityWithMetadata<InventoryItem>> inventoryItems = 
                entityService.search(inventoryModelSpec, groupCondition, InventoryItem.class);

            // Check if inventory item exists
            if (inventoryItems.isEmpty()) {
                return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                            "Product not found in inventory: " + lineItem.getProductId());
            }

            InventoryItem inventoryItem = inventoryItems.get(0).entity();

            // Check if sufficient stock is available
            Integer totalAvailable = inventoryItem.getTotalAvailableStock();
            Integer requiredQuantity = lineItem.getQuantity();

            if (totalAvailable < requiredQuantity) {
                return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                            String.format("Insufficient stock for product %s. Required: %d, Available: %d", 
                                                         lineItem.getProductId(), requiredQuantity, totalAvailable));
            }

            // Check for negative stock (overselling prevention)
            if (inventoryItem.hasNegativeStock()) {
                return EvaluationOutcome.fail(StandardEvalReasonCategories.DATA_QUALITY_FAILURE, 
                                            "Product has negative stock: " + lineItem.getProductId());
            }

            // Check if any location has sufficient stock
            boolean hasLocationWithSufficientStock = inventoryItem.getStockByLocation().values().stream()
                    .anyMatch(stock -> stock.getAvailable() >= requiredQuantity);

            if (!hasLocationWithSufficientStock) {
                return EvaluationOutcome.fail(StandardEvalReasonCategories.BUSINESS_RULE_FAILURE, 
                                            String.format("No single location has sufficient stock for product %s. Required: %d", 
                                                         lineItem.getProductId(), requiredQuantity));
            }

            logger.debug("Inventory check passed for productId: {}, required: {}, available: {}", 
                        lineItem.getProductId(), requiredQuantity, totalAvailable);

            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error checking inventory for productId: {}, orderId: {}", 
                        lineItem.getProductId(), orderId, e);
            return EvaluationOutcome.fail(StandardEvalReasonCategories.EXTERNAL_DEPENDENCY_FAILURE, 
                                        "Failed to check inventory for product: " + lineItem.getProductId());
        }
    }
}
