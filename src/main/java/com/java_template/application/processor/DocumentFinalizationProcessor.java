package com.java_template.application.processor;

import com.java_template.application.entity.document.version_1.Document;
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

/**
 * Processor for finalizing documents
 */
@Component
public class DocumentFinalizationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentFinalizationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DocumentFinalizationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Document.class)
                .validate(this::isValidEntityWithMetadata, "Invalid document entity wrapper")
                .map(this::processDocumentFinalization)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Document> entityWithMetadata) {
        Document entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Document> processDocumentFinalization(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Document> context) {

        EntityWithMetadata<Document> entityWithMetadata = context.entityResponse();
        Document document = entityWithMetadata.entity();

        logger.debug("Finalizing document: {}", document.getDocumentId());

        // Finalize document
        finalizeDocument(document);
        
        // Update timestamps
        document.setUpdatedAt(LocalDateTime.now());

        logger.info("Document {} finalized successfully", document.getDocumentId());
        return entityWithMetadata;
    }

    private void finalizeDocument(Document document) {
        // Validate document is ready for finalization
        if (document.getFileReference() == null || document.getFileReference().trim().isEmpty()) {
            throw new IllegalStateException("Cannot finalize document without file reference");
        }
        
        if (document.getChecksumSha256() == null || document.getChecksumSha256().trim().isEmpty()) {
            throw new IllegalStateException("Cannot finalize document without checksum");
        }
        
        // Set effective date if not already set
        if (document.getEffectiveDate() == null) {
            document.setEffectiveDate(java.time.LocalDate.now());
        }
        
        // Log finalization
        logger.info("Document {} finalized with version {}", document.getDocumentId(), document.getVersionLabel());
    }
}
