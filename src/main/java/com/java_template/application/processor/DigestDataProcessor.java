package com.java_template.application.processor;

import com.java_template.application.entity.DigestData;
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

import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import java.util.function.Function;

@Component
public class DigestDataProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DigestDataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("DigestDataProcessor initialized with SerializerFactory, EntityService, and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestData for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestData.class)
            .validate(DigestData::isValid, "Invalid DigestData entity")
            .map(this::processDigestDataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestDataProcessor".equals(modelSpec.operationName()) &&
               "digestData".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestData processDigestDataLogic(DigestData digestData) {
        // Business logic to compile the retrieved data into the specified digest format

        // Example simplistic logic: if format is not set, default to "HTML";
        if (digestData.getFormat() == null || digestData.getFormat().isBlank()) {
            digestData.setFormat("HTML");
        }

        // Here you would add logic to parse and format the retrievedData field as required
        // For example, transform JSON string to HTML or plain text based on format

        // Simulate compilation by modifying content if needed (digestData has no direct content property, so this is a placeholder for real processing)
        // Real logic should be based on the actual prototype which was not provided in detail for DigestData

        return digestData;
    }

}
