package com.example.application.processor;

import com.example.application.entity.alert.version_1.Alert;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * EnrichWithCustomerHistory Processor - Alert Review and Case Management Workflow
 * 
 * Enriches alerts with customer history and behavioral data.
 * Provides context for analyst review and decision-making.
 */
@Component
public class EnrichWithCustomerHistory implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichWithCustomerHistory.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichWithCustomerHistory(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Enriching alert with customer history for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Alert.class)
                .validate(this::isValidEntityWithMetadata, "Invalid alert entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Alert> entityWithMetadata) {
        Alert entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Alert> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Alert> context) {

        EntityWithMetadata<Alert> entityWithMetadata = context.entityResponse();
        Alert alert = entityWithMetadata.entity();

        logger.debug("Enriching alert {} with customer history", alert.getId());

        // Enrich with customer history data
        enrichCustomerHistory(alert);

        // Update metadata
        if (alert.getMetadata() == null) {
            alert.setMetadata(new HashMap<>());
        }
        alert.getMetadata().put("enriched", true);
        alert.getMetadata().put("enrichmentTime", System.currentTimeMillis());

        logger.info("Alert {} enriched with customer history", alert.getId());
        return entityWithMetadata;
    }

    private void enrichCustomerHistory(Alert alert) {
        if (alert.getMetadata() == null) {
            alert.setMetadata(new HashMap<>());
        }

        // Simulate customer history enrichment
        Map<String, Object> customerHistory = new HashMap<>();
        
        // Account age
        customerHistory.put("accountAgeMonths", 24);
        
        // Transaction history
        customerHistory.put("totalTransactions", 156);
        customerHistory.put("averageTransactionAmount", 2500.00);
        customerHistory.put("maxTransactionAmount", 15000.00);
        
        // Previous alerts
        customerHistory.put("previousAlerts", 2);
        customerHistory.put("previousCases", 0);
        
        // Behavioral patterns
        customerHistory.put("typicalTransactionFrequency", "daily");
        customerHistory.put("typicalTransactionCountries", java.util.List.of("US", "CA", "UK"));
        customerHistory.put("unusualActivityDetected", false);
        
        // Risk indicators
        customerHistory.put("previousSuspiciousActivity", false);
        customerHistory.put("complianceScore", 85);
        customerHistory.put("riskRating", "LOW");

        alert.getMetadata().put("customerHistory", customerHistory);
        
        // Add behavioral context
        Map<String, Object> behavioralContext = new HashMap<>();
        behavioralContext.put("isOutOfPattern", false);
        behavioralContext.put("deviationScore", 0.15);
        behavioralContext.put("similarPastTransactions", 12);
        alert.getMetadata().put("behavioralContext", behavioralContext);
    }
}

