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
import java.lang.reflect.Field;

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

        try {
            // Use reflection to read/write fields to avoid relying on generated Lombok accessors at compile time.
            String status = toStringSafe(getField(shipment, "status"));
            if (status == null || status.isBlank()) {
                setField(shipment, "status", "PICKING");
                logger.debug("Defaulted shipment status to PICKING for shipment technical id (may be persisted by workflow).");
            }

            List<?> lines = (List<?>) getField(shipment, "lines");
            String shipmentId = toStringSafe(getField(shipment, "shipmentId"));

            if (lines == null || lines.isEmpty()) {
                logger.warn("Shipment {} has no lines to process", shipmentId != null ? shipmentId : "unknown");
                return shipment;
            }

            for (Object lineObj : lines) {
                if (lineObj == null) continue;

                Integer qtyOrdered = toIntegerSafe(getField(lineObj, "qtyOrdered"));
                if (qtyOrdered == null || qtyOrdered <= 0) {
                    logger.warn("Skipping line with invalid qtyOrdered for shipment {}: sku={}", shipmentId, toStringSafe(getField(lineObj, "sku")));
                    // ensure picked/shipped counters are present
                    if (getField(lineObj, "qtyPicked") == null) setField(lineObj, "qtyPicked", 0);
                    if (getField(lineObj, "qtyShipped") == null) setField(lineObj, "qtyShipped", 0);
                    continue;
                }

                if (getField(lineObj, "qtyPicked") == null) setField(lineObj, "qtyPicked", 0);
                if (getField(lineObj, "qtyShipped") == null) setField(lineObj, "qtyShipped", 0);

                String sku = toStringSafe(getField(lineObj, "sku"));
                if (sku == null || sku.isBlank()) {
                    logger.warn("Skipping line with empty sku for shipment {}", shipmentId);
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
                        logger.warn("No product found for sku={} while processing shipment {}", sku, shipmentId);
                        continue;
                    }

                    // Use first matching product
                    DataPayload payload = products.get(0);
                    Product product = objectMapper.treeToValue(payload.getData(), Product.class);

                    Integer currentQty = toIntegerSafe(getField(product, "quantityAvailable"));
                    if (currentQty == null) currentQty = 0;
                    int updatedQty = currentQty - qtyOrdered;
                    if (updatedQty < 0) {
                        logger.warn("Product {} stock would go negative (current={} ordered={}). Setting to 0.", sku, currentQty, qtyOrdered);
                        updatedQty = 0;
                    }
                    // Set updated quantity with reflection
                    setField(product, "quantityAvailable", updatedQty);

                    // Persist the updated product using the technical id from payload meta
                    String technicalId = payload.getMeta() != null && payload.getMeta().get("entityId") != null
                            ? payload.getMeta().get("entityId").asText()
                            : null;

                    if (technicalId == null || technicalId.isBlank()) {
                        logger.error("Missing technical id for product sku={} - cannot update stock", sku);
                        continue;
                    }

                    entityService.updateItem(UUID.fromString(technicalId), product).get();
                    logger.info("Decremented product {} quantityAvailable from {} to {} for shipment {}", sku, currentQty, updatedQty, shipmentId);

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while updating product for shipment {} sku={}", shipmentId, sku, ie);
                } catch (ExecutionException ee) {
                    logger.error("Execution error while updating product for shipment {} sku={}: {}", shipmentId, sku, ee.getMessage(), ee);
                } catch (Exception ex) {
                    logger.error("Unexpected error while processing shipment {} sku={}: {}", shipmentId, sku, ex.getMessage(), ex);
                }
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in CreateShipmentProcessor: {}", ex.getMessage(), ex);
        }

        return shipment;
    }

    // Reflection helpers
    private Object getField(Object target, String fieldName) {
        if (target == null) return null;
        Class<?> cls = target.getClass();
        Field field = null;
        while (cls != null) {
            try {
                field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException nsf) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException iae) {
                logger.debug("IllegalAccess when getting field '{}' on {}: {}", fieldName, target.getClass().getName(), iae.getMessage());
                return null;
            }
        }
        return null;
    }

    private void setField(Object target, String fieldName, Object value) {
        if (target == null) return;
        Class<?> cls = target.getClass();
        Field field = null;
        while (cls != null) {
            try {
                field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                // attempt type-coercion for numeric fields
                Class<?> type = field.getType();
                if (value != null && Number.class.isAssignableFrom(type)) {
                    Number n = toNumber(value);
                    if (type == Integer.class) {
                        field.set(target, n != null ? n.intValue() : null);
                    } else if (type == Long.class) {
                        field.set(target, n != null ? n.longValue() : null);
                    } else if (type == Double.class) {
                        field.set(target, n != null ? n.doubleValue() : null);
                    } else {
                        field.set(target, value);
                    }
                } else {
                    field.set(target, value);
                }
                return;
            } catch (NoSuchFieldException nsf) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException iae) {
                logger.debug("IllegalAccess when setting field '{}' on {}: {}", fieldName, target.getClass().getName(), iae.getMessage());
                return;
            } catch (Exception e) {
                logger.debug("Failed to set field '{}' on {}: {}", fieldName, target.getClass().getName(), e.getMessage());
                return;
            }
        }
    }

    private Integer toIntegerSafe(Object o) {
        Number n = toNumber(o);
        return n != null ? n.intValue() : null;
    }

    private Number toNumber(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return (Number) o;
        if (o instanceof String) {
            try {
                String s = ((String) o).trim();
                if (s.isEmpty()) return null;
                if (s.contains(".")) return Double.parseDouble(s);
                return Long.parseLong(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String toStringSafe(Object o) {
        return o != null ? o.toString() : null;
    }
}