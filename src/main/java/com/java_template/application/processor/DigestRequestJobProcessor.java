package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequestJob;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;

@Component
public class DigestRequestJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DigestRequestJobProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        logger.info("DigestRequestJobProcessor initialized with SerializerFactory, EntityService, and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequestJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestRequestJob.class)
                .validate(DigestRequestJob::isValid, "Invalid DigestRequestJob entity state")
                .map(this::processDigestRequestJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestJobProcessor".equals(modelSpec.operationName()) &&
                "digestRequestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequestJob processDigestRequestJobLogic(DigestRequestJob job) {
        logger.info("Processing DigestRequestJob with email: {}", job.getEmail());

        try {
            // Update status to PROCESSING
            job.setStatus("PROCESSING");
            entityService.addItem("digestRequestJob", Config.ENTITY_VERSION, job).join();

            // Parse metadata JSON for endpoint and params
            String endpoint = "/pet/findByStatus"; // default
            Map<String, Object> params = new HashMap<>();
            try {
                Map<String,Object> metadataMap = objectMapper.readValue(job.getMetadata(), Map.class);
                if (metadataMap.containsKey("endpoint")) {
                    endpoint = metadataMap.get("endpoint").toString();
                }
                if (metadataMap.containsKey("params")) {
                    Object paramObj = metadataMap.get("params");
                    if (paramObj instanceof Map) {
                        params = (Map<String,Object>) paramObj;
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to parse metadata JSON: {}", e.getMessage());
            }

            // Build query string for params
            StringBuilder queryBuilder = new StringBuilder();
            if (!params.isEmpty()) {
                queryBuilder.append("?");
                params.forEach((k,v) -> {
                    queryBuilder.append(k).append("=").append(v.toString()).append("&");
                });
                queryBuilder.setLength(queryBuilder.length() - 1); // remove trailing &
            }

            String url = "https://petstore.swagger.io/v2" + endpoint + queryBuilder.toString();

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String retrievedData = response.body();

                    // Create DigestData entity
                    com.java_template.application.entity.DigestData digestData = new com.java_template.application.entity.DigestData();
                    digestData.setJobTechnicalId(job.getModelKey().getName() + "-" + job.getEmail()); // Using a string identifier as example
                    digestData.setRetrievedData(retrievedData);
                    digestData.setFormat("HTML"); // defaulting to HTML for now
                    digestData.setCreatedAt(Instant.now());

                    CompletableFuture<UUID> dataIdFuture = entityService.addItem("digestData", Config.ENTITY_VERSION, digestData);
                    UUID digestDataId = dataIdFuture.get();

                    logger.info("DigestData created with ID: {}", digestDataId);

                    // Here, ideally, you would trigger processDigestData or next steps
                    // But processors only modify current entity, so no direct call here

                } else {
                    logger.error("External API call failed with status: {}", response.statusCode());
                    job.setStatus("FAILED");
                    entityService.addItem("digestRequestJob", Config.ENTITY_VERSION, job).join();
                }
            } catch (Exception e) {
                logger.error("Exception during external API call: {}", e.getMessage());
                job.setStatus("FAILED");
                entityService.addItem("digestRequestJob", Config.ENTITY_VERSION, job).join();
            }
        } catch (Exception e) {
            logger.error("Error processing DigestRequestJob: {}", e.getMessage());
        }

        return job;
    }

}