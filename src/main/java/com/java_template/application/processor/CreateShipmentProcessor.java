package com.java_template.application.processor;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class CreateShipmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateShipmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CreateShipmentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Shipment entity) {
        return entity != null && entity.isValid();
    }

    private Shipment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();

        // Ensure shipment status defaults to PICKING when created if not set
        if (shipment.getStatus() == null || shipment.getStatus().isBlank()) {
            shipment.setStatus("PICKING");
        }

        if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
            logger.warn("Shipment {} has no lines to process", shipment.getShipmentId());
            return shipment;
        }

        // For each shipment line: ensure numeric fields are non-null and decrement product stock by qtyOrdered
        for (Shipment.ShipmentLine line : shipment.getLines()) {
            if (line == null) continue;

            Integer qtyOrdered = line.getQtyOrdered();
            if (qtyOrdered == null || qtyOrdered <= 0) {
                logger.warn("Skipping line with invalid qtyOrdered for shipment {}: sku={}", shipment.getShipmentId(), line.getSku());
                // ensure picked/shipped counters are present
                if (line.getQtyPicked() == null) line.setQtyPicked(0);
                if (line.getQtyShipped() == null) line.setQtyShipped(0);
                continue;
            }

            if (line.getQtyPicked() == null) line.setQtyPicked(0);
            if (line.getQtyShipped() == null) line.setQtyShipped(0);

            String sku = line.getSku();
            if (sku == null || sku.isBlank()) {
                logger.warn("Skipping line with empty sku for shipment {}", shipment.getShipmentId());
                continue;
            }

            try {
                // Build simple condition to find product by sku
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.sku", "EQUALS", sku)
                );

                List<DataPayload> products = entityService.getItemsByCondition(
                        Product.ENTITY_NAME,
                        Product.ENTITY_VERSION,
                        condition,
                        true
                ).get();

                if (products == null || products.isEmpty()) {
                    logger.warn("No product found for sku={} while processing shipment {}", sku, shipment.getShipmentId());
                    continue;
                }

                // Use first matching product
                DataPayload payload = products.get(0);
                Product product = objectMapper.treeToValue(payload.getData(), Product.class);

                Integer currentQty = product.getQuantityAvailable();
                if (currentQty == null) currentQty = 0;
                int updatedQty = currentQty - qtyOrdered;
                if (updatedQty < 0) {
                    logger.warn("Product {} stock would go negative (current={} ordered={}). Setting to 0.", sku, currentQty, qtyOrdered);
                    updatedQty = 0;
                }
                product.setQuantityAvailable(updatedQty);

                // Persist the updated product using the technical id from payload meta
                String technicalId = payload.getMeta() != null && payload.getMeta().get("entityId") != null
                        ? payload.getMeta().get("entityId").asText()
                        : null;

                if (technicalId == null || technicalId.isBlank()) {
                    logger.error("Missing technical id for product sku={} - cannot update stock", sku);
                    continue;
                }

                entityService.updateItem(UUID.fromString(technicalId), product).get();
                logger.info("Decremented product {} quantityAvailable from {} to {} for shipment {}", sku, currentQty, updatedQty, shipment.getShipmentId());

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while updating product for shipment {} sku={}", shipment.getShipmentId(), sku, ie);
            } catch (ExecutionException ee) {
                logger.error("Execution error while updating product for shipment {} sku={}: {}", shipment.getShipmentId(), sku, ee.getMessage(), ee);
            } catch (Exception ex) {
                logger.error("Unexpected error while processing shipment {} sku={}: {}", shipment.getShipmentId(), sku, ex.getMessage(), ex);
            }
        }

        return shipment;
    }
}