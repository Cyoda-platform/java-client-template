package com.java_template.application.processor;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
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

import java.math.BigDecimal;

@Component
public class EnrichItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventoryItem for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(InventoryItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(InventoryItem entity) {
        return entity != null && entity.isValid();
    }

    private InventoryItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<InventoryItem> context) {
        InventoryItem entity = context.entity();
        try {
            // If unitPrice missing try to infer from name hints (e.g., "free" or contains currency symbol)
            if (entity.getUnitPrice() == null) {
                String name = entity.getName();
                if (name != null) {
                    String lower = name.toLowerCase();
                    if (lower.contains("free") || lower.contains("sample")) {
                        entity.setUnitPrice(BigDecimal.ZERO);
                    } else if (lower.matches(".*\\$\\d+(\\.\\d{1,2})?.*")) {
                        // crude extraction of $number in name
                        int dollar = lower.indexOf('$');
                        String numPart = lower.substring(dollar + 1).replaceAll("[^0-9.]", "");
                        if (!numPart.isEmpty()) {
                            try {
                                entity.setUnitPrice(new BigDecimal(numPart));
                            } catch (Exception ex) {
                                logger.debug("Unable to parse price from name: {}", name);
                            }
                        }
                    }
                }
            }

            // If location missing and sourceId available, set a default location hint
            if (entity.getLocation() == null && entity.getSourceId() != null) {
                String src = entity.getSourceId();
                entity.setLocation("DEFAULT-" + (src.length() > 8 ? src.substring(0, 8) : src));
            }

            // Determine next status: if required enrichment fields still missing, mark ENRICHING
            if (entity.getUnitPrice() == null || entity.getLocation() == null) {
                entity.setStatus("ENRICHING");
            } else {
                // normalized and enriched enough to go to validation step
                entity.setStatus("VALIDATING");
            }
        } catch (Exception e) {
            logger.error("Error during enrichment for item {}: {}", entity.getTechnicalId(), e.getMessage(), e);
            // keep existing status on failure
        }
        return entity;
    }
}
