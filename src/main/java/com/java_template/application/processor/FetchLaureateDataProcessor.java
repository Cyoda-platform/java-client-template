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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class FetchLaureateDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchLaureateDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchLaureateDataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Fetching laureate data for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity")
            .map(this::fetchLaureateData)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getIngestionSource() != null && !job.getIngestionSource().isEmpty();
    }

    private Job fetchLaureateData(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        String sourceUrl = job.getIngestionSource();
        try {
            URL url = new URL(sourceUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            int status = conn.getResponseCode();
            if (status != 200) {
                logger.error("Failed to fetch data from {} with status code {}", sourceUrl, status);
                job.setStatus("FAILED");
                job.setErrorDetails("Failed to fetch data, HTTP status: " + status);
                return job;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            conn.disconnect();

            // Parse JSON and Extract laureate records
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(content.toString());
            JsonNode records = rootNode.path("records");
            if (!records.isArray()) {
                logger.error("Unexpected JSON structure: 'records' is not an array");
                job.setStatus("FAILED");
                job.setErrorDetails("Invalid JSON structure from ingestion source");
                return job;
            }

            // Store the raw JSON as resultSummary for record count
            int count = records.size();
            job.setResultSummary("Fetched " + count + " laureate records");

            // Attach laureate data to job for downstream processing (could be in context or a temporary holder)
            // Here, we simulate by storing JSON string in errorDetails temporarily
            job.setErrorDetails(content.toString());
            job.setStatus("INGESTING");

        } catch (Exception e) {
            logger.error("Exception during fetching laureate data", e);
            job.setStatus("FAILED");
            job.setErrorDetails(e.getMessage());
        }
        return job;
    }
}
