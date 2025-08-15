package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@Component
public class ProductValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProductValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product product) {
        return product != null && product.isValid();
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<Product> processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();
        List<String> errors = new ArrayList<>();

        // Validate fields with business rules
        if (product.getId() == null || product.getId().isBlank()) {
            errors.add("id is required");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            errors.add("name is required");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
            errors.add("price must be >= 0");
        }
        if (product.getCurrency() == null || product.getCurrency().isBlank()) {
            errors.add("currency is required");
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < 0) {
            errors.add("stockQuantity must be >= 0");
        }

        try {
            // Load existing persisted product technicalId if provided in context attributes
            String technicalId = context.attributes() != null ? (String) context.attributes().get("technicalId") : null;
            boolean isUpdate = technicalId != null && !technicalId.isBlank();

            if (!errors.isEmpty()) {
                product.setStatus("ERROR");
                // Build ObjectNode payload with errors and status to persist without creating duplicate records
                ObjectNode node = com.java_template.common.util.Json.mapper().convertValue(product, ObjectNode.class);
                // ensure errors array present
                if (!node.has("errors")) node.putArray("errors");
                for (String e : errors) node.withArray("errors").add(e);
                node.put("status", "ERROR");

                if (isUpdate) {
                    try {
                        entityService.updateItem(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            UUID.fromString(technicalId),
                            node
                        ).whenComplete((id, ex) -> {
                            if (ex != null) logger.error("Failed to persist product with errors (update)", ex);
                        });
                    } catch (Exception ex) {
                        logger.error("Failed to schedule update for product errors", ex);
                    }
                } else {
                    CompletableFuture<java.util.UUID> fut = entityService.addItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        node
                    );
                    fut.whenComplete((id, ex) -> {
                        if (ex != null) logger.error("Failed to persist product with errors (add)", ex);
                    });
                }
            } else {
                product.setStatus("ACTIVE");
                String now = Instant.now().toString();
                if (product.getCreatedAt() == null || product.getCreatedAt().isBlank()) product.setCreatedAt(now);
                product.setUpdatedAt(now);
                // clear errors if any - not modeled on entity directly so just persist
                if (isUpdate) {
                    try {
                        entityService.updateItem(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            UUID.fromString(technicalId),
                            product
                        ).whenComplete((id, ex) -> {
                            if (ex != null) logger.error("Failed to persist product after validation (update)", ex);
                        });
                    } catch (Exception ex) {
                        logger.error("Failed to schedule product update", ex);
                    }
                } else {
                    CompletableFuture<java.util.UUID> fut = entityService.addItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        product
                    );
                    fut.whenComplete((id, ex) -> {
                        if (ex != null) logger.error("Failed to persist product after validation (add)", ex);
                    });
                }
            }
        } catch (Exception ex) {
            logger.error("Error processing product validation", ex);
            product.setStatus("ERROR");
        }

        return context;
    }
}
