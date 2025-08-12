package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FetchNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchNobelLaureatesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchNobelLaureatesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        // Basic validation: job must not be null and must have jobId and parameters
        if (job == null) {
            logger.error("Job entity is null");
            return false;
        }
        if (job.getJobId() == null || job.getJobId().isEmpty()) {
            logger.error("Job jobId is missing");
            return false;
        }
        if (job.getParameters() == null || job.getParameters().isEmpty()) {
            logger.error("Job parameters are missing");
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Transition the job status to INGESTING
        job.setStatus("INGESTING");
        logger.info("Job {} status updated to INGESTING", job.getJobId());

        // Extract parameters JSON string from job and parse it
        String parametersJson = job.getParameters();
        Map<String, Object> parametersMap;
        try {
            parametersMap = context.jsonMapper().readValue(parametersJson, Map.class);
        } catch (Exception e) {
            logger.error("Failed to parse job parameters JSON", e);
            job.setStatus("FAILED");
            job.setResultSummary("Failed to parse job parameters JSON");
            return job;
        }

        // Fetch Nobel laureates data from OpenDataSoft API
        List<Map<String, Object>> laureatesData;
        try {
            laureatesData = fetchLaureatesData(parametersMap);
        } catch (Exception e) {
            logger.error("Failed to fetch laureates data from API", e);
            job.setStatus("FAILED");
            job.setResultSummary("Failed to fetch laureates data from API");
            return job;
        }

        // For each laureate data, create a new immutable Laureate entity
        int successCount = 0;
        int failureCount = 0;
        for (Map<String, Object> laureateData : laureatesData) {
            try {
                createLaureateEntity(laureateData, context);
                successCount++;
            } catch (Exception e) {
                logger.error("Failed to create Laureate entity", e);
                failureCount++;
            }
        }

        // Update job status based on ingestion results
        if (failureCount == 0) {
            job.setStatus("SUCCEEDED");
            job.setResultSummary("Successfully ingested " + successCount + " laureates.");
            logger.info("Job {} ingestion succeeded with {} laureates ingested", job.getJobId(), successCount);
        } else {
            job.setStatus("FAILED");
            job.setResultSummary("Ingestion completed with " + failureCount + " failures.");
            logger.warn("Job {} ingestion failed with {} failed laureate creations", job.getJobId(), failureCount);
        }

        // Set completedAt timestamp
        job.setCompletedAt(OffsetDateTime.now());

        return job;
    }

    private List<Map<String, Object>> fetchLaureatesData(Map<String, Object> parameters) throws Exception {
        // Build the OpenDataSoft API URL with parameters
        // Default dataset is "nobel-prize-laureates" if not specified
        String baseUrl = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

        // Here, parameters could be used to customize API call if needed (e.g., filtering)

        // Use java.net.http.HttpClient to fetch data
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(baseUrl))
            .GET()
            .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch data, status code: " + response.statusCode());
        }

        // Parse JSON response
        Map<String, Object> responseMap = context.jsonMapper().readValue(response.body(), Map.class);
        if (!responseMap.containsKey("records")) {
            throw new RuntimeException("Response does not contain records");
        }

        // Extract laureate records
        List<Map<String, Object>> records = (List<Map<String, Object>>) responseMap.get("records");

        // Extract fields from each record's "fields" map
        List<Map<String, Object>> laureatesData = records.stream()
            .map(record -> (Map<String, Object>) record.get("fields"))
            .collect(Collectors.toList());

        return laureatesData;
    }

    private void createLaureateEntity(Map<String, Object> laureateData, ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        // Construct new Laureate entity with data from laureateData map
        com.java_template.application.entity.laureate.version_1.Laureate laureate = new com.java_template.application.entity.laureate.version_1.Laureate();

        // Set immutable laureateId from id field (convert to string)
        Object idObj = laureateData.get("id");
        if (idObj != null) {
            laureate.setLaureateId(String.valueOf(idObj));
        }

        // Set other fields (nullable fields handled accordingly)
        laureate.setFirstname((String) laureateData.get("firstname"));
        laureate.setSurname((String) laureateData.get("surname"));
        laureate.setBorn((String) laureateData.get("born"));
        laureate.setDied((String) laureateData.get("died"));
        laureate.setBorncountry((String) laureateData.get("borncountry"));
        laureate.setBorncountrycode((String) laureateData.get("borncountrycode"));
        laureate.setBorncity((String) laureateData.get("borncity"));
        laureate.setGender((String) laureateData.get("gender"));
        laureate.setYear((String) laureateData.get("year"));
        laureate.setCategory((String) laureateData.get("category"));
        laureate.setMotivation((String) laureateData.get("motivation"));
        laureate.setName((String) laureateData.get("name"));
        laureate.setCity((String) laureateData.get("city"));
        laureate.setCountry((String) laureateData.get("country"));

        // TODO: Trigger processing of Laureate entity, e.g., enqueue for further workflow
        // Note: This would normally be done via event publishing or workflow engine integration
        logger.info("Created Laureate entity with id {}", laureate.getLaureateId());
    }
}
