package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class MetricsCalculatorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCalculatorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MetricsCalculatorProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MetricsCalculator for request: {}", request.getId());

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

    private boolean isValidEntity(Product entity) {
        return entity != null && entity.getTechnicalId() != null;
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product p = context.entity();
        try {
            // Compute simple KPIs based on available fields
            double salesVolume = 0.0;
            double revenue = 0.0;

            // If salesHistory available, sum unitsSold and revenue
            if (p.getSalesHistory() != null && !p.getSalesHistory().isEmpty()) {
                for (Object entryObj : p.getSalesHistory()) {
                    if (entryObj instanceof java.util.Map) {
                        java.util.Map<?, ?> entry = (java.util.Map<?, ?>) entryObj;
                        Number units = entry.get("unitsSold") instanceof Number ? (Number) entry.get("unitsSold") : null;
                        Number rev = entry.get("revenue") instanceof Number ? (Number) entry.get("revenue") : null;
                        if (units != null) salesVolume += units.doubleValue();
                        if (rev != null) revenue += rev.doubleValue();
                    }
                }
            } else {
                // fallback heuristic: estimate based on stockLevel
                if (p.getStockLevel() != null) salesVolume = p.getStockLevel() * 0.1;
                if (p.getPrice() != null) revenue = salesVolume * p.getPrice();
            }

            if (p.getMetrics() == null) p.setMetrics(new java.util.HashMap<>());
            p.getMetrics().put("salesVolume", salesVolume);
            p.getMetrics().put("revenue", revenue);

            // turnoverRate calculation: unitsSold / averageStockLevel
            Double avgStock = null;
            if (p.getMetrics().containsKey("averageStockLevel")) {
                Object o = p.getMetrics().get("averageStockLevel");
                if (o instanceof Number) avgStock = ((Number) o).doubleValue();
            }
            if (avgStock == null || avgStock == 0.0) {
                p.getMetrics().put("turnoverRate", null);
            } else {
                p.getMetrics().put("turnoverRate", salesVolume / avgStock);
            }

            p.setLastUpdated(OffsetDateTime.now().toString());

            logger.info("Metrics calculated for productId={}", p.getProductId());
        } catch (Exception e) {
            logger.error("Error calculating metrics for productId={}", p != null ? p.getProductId() : "<unknown>", e);
        }
        // IMPORTANT: do not call entityService.updateItem on the entity that triggered this processor.
        // Cyoda will persist changes to the Product entity automatically based on workflow.
        return p;
    }
}
