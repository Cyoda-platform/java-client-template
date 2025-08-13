package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.catfactjob.version_1.CatFactJob;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class FetchCatFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchCatFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FetchCatFactProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchCatFact for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFactJob.class)
            .validate(this::isValidEntity, "Invalid CatFactJob entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CatFactJob entity) {
        return entity != null && entity.getScheduledAt() != null && !entity.getScheduledAt().isEmpty();
    }

    private CatFactJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFactJob> context) {
        CatFactJob entity = context.entity();
        try {
            // Call Cat Fact API to retrieve a random cat fact
            URL url = new URL("https://catfact.ninja/fact");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            int status = con.getResponseCode();
            if (status == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder content = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                con.disconnect();

                JsonNode json = objectMapper.readTree(content.toString());
                String catFact = json.path("fact").asText("");
                logger.info("Fetched cat fact: {}", catFact);

                // Store the cat fact in the entity
                entity.setCatFact(catFact);
                entity.setStatus("fact_retrieved");

                // Persist updated entity
                CompletableFuture<UUID> updateFuture = entityService.addItem(
                    CatFactJob.ENTITY_NAME,
                    String.valueOf(CatFactJob.ENTITY_VERSION),
                    entity
                );
                updateFuture.get();
            } else {
                logger.error("Failed to fetch cat fact, HTTP status: {}", status);
                entity.setStatus("fact_retrieval_failed");
            }
        } catch (Exception e) {
            logger.error("Exception while fetching cat fact", e);
            entity.setStatus("fact_retrieval_failed");
        }
        return entity;
    }
}
