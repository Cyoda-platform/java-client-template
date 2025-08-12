package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class FetchDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final String API_ENDPOINT = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

    public FetchDataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job fetch data for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job state for data fetch")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        // Validate entity is not null and status is INGESTING
        if (entity == null) {
            logger.error("Job entity is null in FetchDataProcessor");
            return false;
        }
        if (!"INGESTING".equalsIgnoreCase(entity.getStatus())) {
            logger.error("Job status is not INGESTING in FetchDataProcessor");
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        try {
            // Fetch data from OpenDataSoft API
            logger.info("Fetching laureates data from API");
            URL url = new URL(API_ENDPOINT);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int status = connection.getResponseCode();
            if (status != 200) {
                logger.error("Failed to fetch data. HTTP status: {}", status);
                // Mark job as failed
                job.setStatus("FAILED");
                job.setDetails("Failed to fetch data from API, HTTP status: " + status);
                return job;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            // Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(content.toString());
            JsonNode recordsNode = rootNode.path("records");

            if (!recordsNode.isArray()) {
                logger.error("Records node is not an array");
                job.setStatus("FAILED");
                job.setDetails("API response format invalid: records node not an array");
                return job;
            }

            // Store fetched laureates data in job details as JSON string for next processor
            job.setDetails(recordsNode.toString());
            logger.info("Fetched {} laureate records", recordsNode.size());

        } catch (Exception e) {
            logger.error("Exception while fetching data", e);
            job.setStatus("FAILED");
            job.setDetails("Exception during data fetch: " + e.getMessage());
        }

        return job;
    }
}
