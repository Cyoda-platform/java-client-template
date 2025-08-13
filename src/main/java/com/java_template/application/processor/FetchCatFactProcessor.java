package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.catfactjob.version_1.CatFactJob;
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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

@Component
public class FetchCatFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchCatFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchCatFactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchCatFactProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(CatFactJob.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CatFactJob entity) {
        return entity != null && entity.isValid();
    }

    private CatFactJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFactJob> context) {
        CatFactJob entity = context.entity();

        try {
            // External API call to get cat fact
            URL url = new URL("https://catfact.ninja/fact");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status == 200) {
                Scanner scanner = new Scanner(conn.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNext()) {
                    response.append(scanner.nextLine());
                }
                scanner.close();

                String responseBody = response.toString();

                // Extract fact from JSON response
                ObjectNode jsonNode = context.serializer().toObjectNode(responseBody);
                String catFact = jsonNode.has("fact") ? jsonNode.get("fact").asText() : null;

                entity.setFact(catFact);
                entity.setStatus("fact_retrieved");
            } else {
                logger.error("Failed to fetch cat fact, HTTP status: {}", status);
                entity.setStatus("fetch_failed");
            }

            conn.disconnect();
        } catch (Exception e) {
            logger.error("Exception during fetching cat fact", e);
            entity.setStatus("fetch_failed");
        }

        return entity;
    }
}
