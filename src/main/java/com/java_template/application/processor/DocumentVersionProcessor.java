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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class DocumentVersionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentVersionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DocumentVersionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Document.class)
                .validate(this::isValidEntityWithMetadata, "Invalid document wrapper")
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

        logger.debug("Processing versioning for document: {}", document.getDocumentId());

        document.setUpdatedAt(LocalDateTime.now());
        logger.info("Document {} versioning processed", document.getDocumentId());

        return entityWithMetadata;
    }
}
