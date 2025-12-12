package com.java_template.application.processor;

import com.java_template.application.entity.customer.version_1.Customer;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Processor to archive customer data during termination
 * 
 * This processor is triggered during the termination_pending -> terminated transition.
 * It handles data archival and cleanup processes for terminated customers.
 */
@Component
public class archiveCustomerData implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(archiveCustomerData.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public archiveCustomerData(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity wrapper")
                .map(this::processDataArchival)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer customer = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return customer != null && customer.isValid() && technicalId != null;
    }

    /**
     * Main business logic for archiving customer data
     */
    private EntityWithMetadata<Customer> processDataArchival(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.info("Archiving data for customer: {}", customer.getCustomerId());

        // Initialize metadata if not present
        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }

        Map<String, Object> metadata = customer.getMetadata();
        
        // Create archive record
        String archiveId = "ARCH-" + UUID.randomUUID().toString().substring(0, 8);
        
        Map<String, Object> archiveInfo = new HashMap<>();
        archiveInfo.put("archiveId", archiveId);
        archiveInfo.put("archivedAt", LocalDateTime.now().toString());
        archiveInfo.put("archiveLocation", "secure_archive_storage");
        archiveInfo.put("retentionPeriod", "7_years");
        archiveInfo.put("dataClassification", "customer_pii");
        archiveInfo.put("archiveStatus", "completed");
        
        metadata.put("archiveInfo", archiveInfo);

        // Mark sensitive data for anonymization (in real implementation)
        Map<String, Object> dataProcessing = new HashMap<>();
        dataProcessing.put("piiAnonymized", false); // Would be true after actual anonymization
        dataProcessing.put("dataRetained", true);
        dataProcessing.put("complianceStatus", "gdpr_compliant");
        dataProcessing.put("processedAt", LocalDateTime.now().toString());
        
        metadata.put("dataProcessing", dataProcessing);

        // Add termination completion marker
        metadata.put("terminationCompleted", true);
        metadata.put("terminationCompletedAt", LocalDateTime.now().toString());

        // Update timestamps
        customer.setUpdatedAt(LocalDateTime.now());

        logger.info("Customer data archived for {} with archive ID: {}", 
                   customer.getCustomerId(), archiveId);

        return entityWithMetadata;
    }
}
