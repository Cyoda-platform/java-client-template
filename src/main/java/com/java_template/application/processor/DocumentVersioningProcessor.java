package com.java_template.application.processor;

import com.java_template.application.entity.document.version_1.Document;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
 * Processor for creating new document versions
 */
@Component
public class DocumentVersioningProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentVersioningProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DocumentVersioningProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Document.class)
                .validate(this::isValidEntityWithMetadata, "Invalid document entity wrapper")
                .map(this::processDocumentVersioning)
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

    private EntityWithMetadata<Document> processDocumentVersioning(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Document> context) {

        EntityWithMetadata<Document> entityWithMetadata = context.entityResponse();
        Document document = entityWithMetadata.entity();

        logger.debug("Creating new version for document: {}", document.getDocumentId());

        // Process document versioning
        processVersioning(document);
        
        // Update timestamps
        document.setUpdatedAt(LocalDateTime.now());

        logger.info("Document versioning processed for document {}", document.getDocumentId());
        return entityWithMetadata;
    }

    private void processVersioning(Document document) {
        // Validate document can be versioned (must be final)
        if (!"final".equals(document.getStatus())) {
            throw new IllegalStateException("Can only create versions from final documents");
        }
        
        // Log versioning - this would supersede the current document
        logger.info("Document {} superseded by new version", document.getDocumentId());
        
        // TODO: When creating a new version, a new Document entity would be created
        // with the current document as the parent and incremented version number
        logger.info("New version will be created for document {}", document.getDocumentId());
    }
}
