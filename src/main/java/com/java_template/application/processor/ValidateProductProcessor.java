package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class ValidateProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidateProductProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid product state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product entity) {
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();
        try {
            if (product.getPrice() == null || product.getPrice() <= 0) {
                logger.warn("Product {} has invalid price {}", product.getProductId(), product.getPrice());
                product.setStatus("PendingValidation");
                return product;
            }
            if (product.getSku() == null || product.getSku().isBlank()) {
                logger.warn("Product {} missing SKU", product.getProductId());
                product.setStatus("NeedsReview");
                return product;
            }

            // Duplicate SKU check
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.sku", "EQUALS", product.getSku())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode results = itemsFuture.get(5, TimeUnit.SECONDS);
            if (results != null && results.size() > 0) {
                ObjectNode existing = (ObjectNode) results.get(0);
                String existingTechId = existing.has("technicalId") ? existing.get("technicalId").asText() : null;
                String currentTechId = context.request().getEntityId();
                if (existingTechId != null && !existingTechId.equals(currentTechId)) {
                    logger.warn("Duplicate SKU found for product {} (existingTechId={})", product.getProductId(), existingTechId);
                    product.setStatus("NeedsReview");
                    return product;
                }
            }

            // All good
            product.setStatus("Active");
            logger.info("Product {} activated", product.getProductId());
            return product;
        } catch (Exception e) {
            logger.error("Error validating product {}: {}", product.getProductId(), e.getMessage(), e);
            product.setStatus("PendingValidation");
            return product;
        }
    }
}
