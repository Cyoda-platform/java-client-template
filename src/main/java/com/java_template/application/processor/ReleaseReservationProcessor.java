package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartLine;
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

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class ReleaseReservationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReleaseReservationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ReleaseReservationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReleaseReservation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state for release")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && ("RESERVED".equals(cart.getStatus()) || "EXPIRED".equals(cart.getStatus()));
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        try {
            if (cart.getLines() == null || cart.getLines().isEmpty()) return cart;

            for (CartLine line : cart.getLines()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.sku", "EQUALS", line.getSku())
                );
                CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
                );
                ArrayNode results = future.get();
                if (results == null || results.size() == 0) continue;
                ObjectNode pNode = (ObjectNode) results.get(0);
                Product p = SerializerFactory.createDefault().getDefaultProcessorSerializer().toEntity(Product.class).read(pNode);
                Integer available = p.getQuantityAvailable() == null ? 0 : p.getQuantityAvailable();
                // Product model currently lacks quantityReserved, so we only increment available back
                p.setQuantityAvailable(available + line.getQty());
                p.setUpdated_at(Instant.now().toString());

                CompletableFuture<ObjectNode> update = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    java.util.UUID.fromString(p.getSku()),
                    SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(p)
                );
                update.get();
            }

            cart.setStatus("EXPIRED");
            cart.setUpdated_at(Instant.now().toString());
            return cart;
        } catch (Exception e) {
            logger.error("Exception while releasing reservation", e);
            return cart;
        }
    }
}
