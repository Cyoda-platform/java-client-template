package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ReleaseReservationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReleaseReservationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReleaseReservationProcessor(SerializerFactory serializerFactory,
                                       EntityService entityService,
                                       ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Reservation for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Reservation.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Reservation entity) {
        return entity != null && entity.isValid();
    }

    private Reservation processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Reservation> context) {
        Reservation reservation = context.entity();

        try {
            // Only release if currently ACTIVE
            if (reservation.getStatus() == null) {
                logger.warn("Reservation {} has null status, skipping release.", reservation.getReservationId());
                return reservation;
            }

            if (!"ACTIVE".equalsIgnoreCase(reservation.getStatus())) {
                logger.info("Reservation {} is not ACTIVE (status={}), skipping release.", reservation.getReservationId(), reservation.getStatus());
                return reservation;
            }

            // Mark reservation as RELEASED
            reservation.setStatus("RELEASED");
            logger.info("Reservation {} set to RELEASED", reservation.getReservationId());

            // Attempt to restore product stock for the reserved SKU.
            // Find product by SKU
            if (reservation.getSku() == null || reservation.getSku().isBlank()) {
                logger.warn("Reservation {} has no SKU, cannot restore product stock.", reservation.getReservationId());
                return reservation;
            }

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.sku", "EQUALS", reservation.getSku())
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode products = itemsFuture.join();
            if (products == null || products.size() == 0) {
                logger.warn("No product found with sku={} while releasing reservation {}", reservation.getSku(), reservation.getReservationId());
                return reservation;
            }

            // Use the first matching product
            ObjectNode productNode = (ObjectNode) products.get(0);
            Product product = objectMapper.treeToValue(productNode, Product.class);

            if (product.getProductId() == null || product.getProductId().isBlank()) {
                logger.warn("Product found for sku={} has no productId, cannot update stock.", reservation.getSku());
                return reservation;
            }

            Integer currentQty = product.getQuantityAvailable();
            if (currentQty == null) currentQty = 0;
            Integer releaseQty = reservation.getQty() == null ? 0 : reservation.getQty();
            int updatedQty = currentQty + releaseQty;
            product.setQuantityAvailable(updatedQty);

            // Persist updated product using EntityService
            try {
                UUID technicalId = UUID.fromString(product.getProductId());
                CompletableFuture<UUID> updateFuture = entityService.updateItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        technicalId,
                        product
                );
                updateFuture.join();
                logger.info("Restored product stock for sku={} by {} units. New qty={}", product.getSku(), releaseQty, updatedQty);
            } catch (Exception e) {
                logger.error("Failed to update product stock for productId={} while releasing reservation {}: {}", product.getProductId(), reservation.getReservationId(), e.getMessage(), e);
            }

        } catch (Exception ex) {
            logger.error("Error while releasing reservation {}: {}", reservation.getReservationId(), ex.getMessage(), ex);
        }

        return reservation;
    }
}