package com.java_template.application.processor;

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
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class processDigestRequest implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public processDigestRequest(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("processDigestRequest initialized with SerializerFactory, EntityService, and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequest for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(DigestRequest.class)
            .validate(DigestRequest::isValid, "Invalid DigestRequest entity")
            .map(this::processDigestRequestLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "processDigestRequest".equals(modelSpec.operationName()) &&
               "digestRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequest processDigestRequestLogic(DigestRequest entity) {
        // Business logic: fetch external data based on metadata or requestPayload, persist DigestData, trigger next processing
        try {
            String endpoint = "/pet/findByStatus";
            String statusParam = "available"; // default

            if (entity.getRequestPayload() != null && !entity.getRequestPayload().isBlank()) {
                JsonNode payloadNode = objectMapper.readTree(entity.getRequestPayload());
                if (payloadNode.has("endpoint")) {
                    endpoint = payloadNode.get("endpoint").asText();
                }
                if (payloadNode.has("params") && payloadNode.get("params").has("status")) {
                    statusParam = payloadNode.get("params").get("status").asText();
                }
            } else if (entity.getMetadata() != null && !entity.getMetadata().isBlank()) {
                // Parse metadata for status
                String metadata = entity.getMetadata();
                if (metadata.startsWith("fetchPetsByStatus=")) {
                    statusParam = metadata.substring("fetchPetsByStatus=".length());
                }
            }

            // Build the URL for external API
            String apiUrl = "https://petstore.swagger.io" + endpoint + "?status=" + statusParam;

            // Fetch external data (simulate here or implement actual HTTP call if allowed)
            // For this example, we simulate fetching by creating a dummy JSON string
            String fetchedData = "{\"pets\": [{\"id\": 1, \"name\": \"Doggo\", \"status\": \"" + statusParam + "\"}]}";

            // Create DigestData entity and save
            com.java_template.application.entity.DigestData digestData = new com.java_template.application.entity.DigestData();
            digestData.setDigestRequestId(entity.getTechnicalId().toString());
            digestData.setRetrievedData(fetchedData);
            digestData.setFormatType("json");

            CompletableFuture<UUID> future = entityService.addItem("digestData", Config.ENTITY_VERSION, digestData);
            future.get(); // wait for completion

            // After saving DigestData, trigger processDigestData (not shown here, but assumed event-driven)

        } catch (Exception e) {
            logger.error("Error processing DigestRequest: {}", e.getMessage(), e);
            // handle error, but do not interrupt flow
        }

        return entity;
    }
}
