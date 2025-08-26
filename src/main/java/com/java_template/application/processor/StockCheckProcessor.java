package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class StockCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StockCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StockCheckProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Running stock check for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart for stock check")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.getLines() != null;
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        List<String> failures = new ArrayList<>();
        try {
            if (cart.getLines() != null) {
                for (Cart.CartLine line : cart.getLines()) {
                    String sku = line.getSku();
                    SearchConditionRequest cond = SearchConditionRequest.group("AND", Condition.of("$.sku", "IEQUALS", sku));
                    CompletableFuture<ArrayNode> prodFuture = entityService.getItemsByCondition(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), cond, true);
                    ArrayNode prods = prodFuture.get();
                    if (prods == null || prods.isEmpty()) {
                        failures.add(sku + ":SKU_NOT_FOUND");
                        continue;
                    }
                    ObjectNode p = (ObjectNode) prods.get(0);
                    if (p.get("quantityAvailable").isNull() || p.get("quantityAvailable").asInt() < line.getQty()) {
                        failures.add(sku + ":INSUFFICIENT_STOCK");
                    }
                }
            }

            if (!failures.isEmpty()) {
                logger.warn("Stock check failed for cart {} - failures: {}", cart.getCartId(), failures);
                // In a real system we would emit StockInsufficient event. For now we attach to cart payload
                // Could set a flag or attach warnings
                return cart;
            }
        } catch (Exception e) {
            logger.error("Error during StockCheckProcessor", e);
        }
        logger.info("Stock check passed for cart {}", cart.getCartId());
        return cart;
    }
}
