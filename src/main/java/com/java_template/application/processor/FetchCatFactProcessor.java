package com.java_template.application.processor;

import com.java_template.application.entity.catfactjob.version_1.CatFactJob;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

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
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                con.disconnect();

                JSONObject json = new JSONObject(content.toString());
                String catFact = json.optString("fact", "");
                logger.info("Fetched cat fact: {}", catFact);

                // Store the cat fact in the entity or context for downstream processing
                // Assuming the CatFactJob entity has a method to set the fact (not defined in POJO) - simulate with a transient field
                // For demonstration, we log it and assume further processing
                // TODO: enhance entity to store catFact if needed

                // Mark success or update status accordingly
                entity.setStatus("fact_retrieved");
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
