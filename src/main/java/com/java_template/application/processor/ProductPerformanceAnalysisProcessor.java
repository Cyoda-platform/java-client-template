package com.java_template.application.processor;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class ProductPerformanceAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductPerformanceAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProductPerformanceAnalysisProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Running performance analysis for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid Product state")
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
            // Simple rules: compute total sales from sales_history
            int totalQty = 0;
            double totalRevenue = 0.0;
            if (product.getSales_history() != null) {
                for (var s : product.getSales_history()) {
                    if (s == null) continue;
                    if (s.getQuantity() != null) totalQty += s.getQuantity();
                    if (s.getRevenue() != null) totalRevenue += s.getRevenue();
                }
            }

            List<String> tags = product.getTags() != null ? new ArrayList<>(product.getTags()) : new ArrayList<>();
            // restock if stock_level below threshold
            if (product.getStock_level() != null && product.getStock_level() < 10) {
                if (!tags.contains("restock_candidate")) tags.add("restock_candidate");
            } else {
                tags.remove("restock_candidate");
            }

            // underperformer if totalQty in period is low or revenue is below cost * qty
            if (totalQty < 5 || totalRevenue < (product.getCost() != null ? product.getCost() * totalQty : 0)) {
                if (!tags.contains("underperformer")) tags.add("underperformer");
            } else {
                tags.remove("underperformer");
            }

            product.setTags(tags);
            // Persist tags update by creating a new entity record (prototype simplification)
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), product
            );
            idFuture.get();
            logger.info("Product performance analysis updated tags for product_id={}", product.getProduct_id());
            return product;
        } catch (Exception ex) {
            logger.error("Error during performance analysis for product {}: {}", product.getProduct_id(), ex.getMessage(), ex);
            return product;
        }
    }
}
