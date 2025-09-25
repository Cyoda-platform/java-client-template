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
 * Processor for withdrawing documents
 */
@Component
public class DocumentWithdrawalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentWithdrawalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DocumentWithdrawalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Document.class)
                .validate(this::isValidEntityWithMetadata, "Invalid document entity wrapper")
                .map(this::processDocumentWithdrawal)
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

    private EntityWithMetadata<Document> processDocumentWithdrawal(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Document> context) {

        EntityWithMetadata<Document> entityWithMetadata = context.entityResponse();
        Document document = entityWithMetadata.entity();

        logger.debug("Withdrawing document: {}", document.getDocumentId());

        // Process document withdrawal
        processWithdrawal(document);
        
        // Update timestamps
        document.setUpdatedAt(LocalDateTime.now());

        logger.info("Document {} withdrawn successfully", document.getDocumentId());
        return entityWithMetadata;
    }

    private void processWithdrawal(Document document) {
        // Log withdrawal
        logger.info("Document {} has been withdrawn", document.getDocumentId());
        
        // Set expiry date to now if not already set
        if (document.getExpiryDate() == null) {
            document.setExpiryDate(java.time.LocalDate.now());
        }
        
        // Log withdrawal reason (would typically come from input)
        logger.info("Document withdrawal processed for document {}", document.getDocumentId());
    }
}
