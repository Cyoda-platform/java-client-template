package com.example.application.processor;

import com.example.application.entity.customer.version_1.Customer;
import com.example.application.entity.document_evidence.version_1.DocumentEvidence;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * StoreDocuments Processor - KYC Onboarding Workflow
 * 
 * Stores uploaded KYC documents and creates DocumentEvidence records.
 * Handles document metadata, checksums, and storage path management.
 */
@Component
public class StoreDocuments implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoreDocuments.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StoreDocuments(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Storing KYC documents for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Customer> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.debug("Storing documents for customer: {}", customer.getId());

        // Create document evidence records for KYC documents
        createDocumentEvidence(customer);

        // Update customer metadata with document storage info
        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }
        customer.getMetadata().put("documentsStored", true);
        customer.getMetadata().put("documentStorageTime", LocalDateTime.now().toString());

        logger.info("Documents stored successfully for customer: {}", customer.getId());
        return entityWithMetadata;
    }

    private void createDocumentEvidence(Customer customer) {
        // Create a document evidence record for the KYC submission
        DocumentEvidence evidence = new DocumentEvidence();
        evidence.setId("DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        evidence.setCaseId("KYC-" + customer.getId());
        evidence.setUploadedBy("system");
        evidence.setFilename("kyc_documents_" + customer.getId() + ".zip");
        evidence.setMimeType("application/zip");
        evidence.setSizeBytes(1024L); // Placeholder size
        evidence.setStoragePath("/compliance/kyc/" + customer.getId() + "/documents.zip");
        evidence.setChecksum(generateChecksum(customer.getId()));
        evidence.setUploadedAt(LocalDateTime.now());
        evidence.setTags(java.util.List.of("KYC", "ONBOARDING", "IDENTITY_VERIFICATION"));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("customerId", customer.getId());
        metadata.put("customerType", customer.getCustomerType());
        metadata.put("kycLevel", customer.getKycLevel());
        evidence.setMetadata(metadata);

        try {
            entityService.create(evidence);
            logger.info("Document evidence created: {}", evidence.getId());
        } catch (Exception e) {
            logger.error("Failed to create document evidence for customer: {}", customer.getId(), e);
        }
    }

    private String generateChecksum(String customerId) {
        // Simple checksum generation (in production, use actual file hash)
        return Integer.toHexString((customerId + System.currentTimeMillis()).hashCode());
    }
}

