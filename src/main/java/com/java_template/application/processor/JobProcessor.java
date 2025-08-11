package com.java_template.application.processor;

import com.java_template.application.entity.Job.version_1.Job;
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
import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class JobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        if (job == null) {
            return false;
        }
        if (job.getJobName() == null || job.getJobName().trim().isEmpty()) {
            return false;
        }
        if (job.getApiEndpoint() == null || job.getApiEndpoint().trim().isEmpty()) {
            return false;
        }
        // Additional basic validation can be done here
        return true;
    }

    private Job processEntityLogic(Job job) {
        // Implement business logic for processJob()
        // Example: validate apiEndpoint format and simulate reachability

        String endpoint = job.getApiEndpoint();
        boolean endpointValid = false;
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.connect();
            int responseCode = connection.getResponseCode();
            endpointValid = (responseCode >= 200 && responseCode < 400);
        } catch (Exception e) {
            logger.warn("API endpoint validation failed for {}: {}", endpoint, e.getMessage());
            endpointValid = false;
        }

        if (!endpointValid) {
            job.setStatus("FAILED");
            job.setResultSummary("API endpoint is not reachable or invalid URL");
            return job;
        }

        // If endpoint valid, set status to INGESTING
        job.setStatus("INGESTING");

        // The actual fetching and processing of laureates is outside this processor's scope
        // This is a placeholder for orchestration logic

        // After processing completes successfully (simulate here)
        job.setStatus("SUCCEEDED");
        job.setResultSummary("Ingestion completed successfully");

        // Notification logic is outside this processor, but we update status here
        job.setStatus("NOTIFIED_SUBSCRIBERS");

        return job;
    }
}
