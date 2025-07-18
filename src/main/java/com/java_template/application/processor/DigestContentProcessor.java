package com.java_template.application.processor;

import com.java_template.application.entity.DigestContent;
import com.java_template.application.entity.DigestRequest;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class DigestContentProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public DigestContentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestContentProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestContent for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestContent.class)
                .validate(DigestContent::isValid, "Invalid DigestContent entity state")
                .map(this::processDigestContentLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestContentProcessor".equals(modelSpec.operationName()) &&
               "digestContent".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestContent processDigestContentLogic(DigestContent content) {
        try {
            processDigestContent(content);
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error processing DigestContent entity id {}", content.getId(), e);
            // Here you might want to handle the error, e.g., wrapping in a RuntimeException or setting error state
        }
        return content;
    }

    private void processDigestContent(DigestContent content) throws ExecutionException, InterruptedException {
        logger.info("Processing DigestContent event for id {}", content.getId());

        if (content.getContent() == null || content.getContent().isBlank()) {
            logger.warn("DigestContent id {} has empty content field", content.getId());
        } else {
            logger.info("DigestContent id {} content length: {}", content.getId(), content.getContent().length());
        }

        try {
            DigestRequest linkedRequest = getDigestRequestById(content.getRequestId());
            logger.info("Linked DigestRequest found for DigestContent id {}: requestId {}", content.getId(), linkedRequest.getId());
        } catch (ResponseStatusException ex) {
            logger.warn("Linked DigestRequest id {} not found for DigestContent id {}", content.getRequestId(), content.getId());
        }
    }

    private DigestRequest getDigestRequestById(String id) throws ExecutionException, InterruptedException {
        // Implementation assumes entityService is available. Since it is not provided,
        // this method should be implemented to fetch the DigestRequest entity by id.
        // This is a stub and will throw UnsupportedOperationException.
        throw new UnsupportedOperationException("EntityService injection and implementation needed for getDigestRequestById");
    }
}
