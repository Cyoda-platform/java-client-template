package com.java_template.application.processor;

import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class FetchDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final int MAX_ATTEMPTS = 3;
    private final long BASE_BACKOFF_MS = 500L;

    public FetchDataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchData for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ExtractionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ExtractionJob entity) {
        return entity != null && entity.getSourceUrl() != null && entity.getParameters() != null;
    }

    private ExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionJob> context) {
        ExtractionJob job = context.entity();
        List<String> endpoints = job.getParameters() != null ? job.getParameters().getEndpoints() : null;
        if (endpoints == null || endpoints.isEmpty()) {
            logger.warn("No endpoints configured for jobId={}", job.getJobId());
            return job;
        }

        for (String endpoint : endpoints) {
            boolean success = false;
            int attempt = 0;
            while (attempt < MAX_ATTEMPTS && !success) {
                attempt++;
                try {
                    String urlStr = job.getSourceUrl();
                    if (!urlStr.endsWith("/") && !endpoint.startsWith("/")) urlStr += "/";
                    String fullUrl = urlStr + (endpoint.startsWith("/") ? endpoint.substring(1) : endpoint);
                    logger.info("Fetching endpoint={} attempt={} for jobId={}", fullUrl, attempt, job.getJobId());

                    URL url = new URL(fullUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(10000);
                    int status = conn.getResponseCode();
                    if (status >= 200 && status < 300) {
                        // For prototype, we don't parse body here. Emit RawDataEvent in real impl.
                        logger.info("Successfully fetched {} for jobId={}", endpoint, job.getJobId());
                        success = true;
                    } else if (status >= 500 && status < 600) {
                        logger.warn("Transient error fetching {} status={} attempt={} for jobId={}", endpoint, status, attempt, job.getJobId());
                        backoff(attempt);
                    } else {
                        logger.error("Non-retriable status={} fetching {} for jobId={}", status, endpoint, job.getJobId());
                        break;
                    }

                } catch (Exception e) {
                    logger.warn("Error fetching endpoint={} attempt={} for jobId={}: {}", endpoint, attempt, job.getJobId(), e.getMessage());
                    backoff(attempt);
                }
            }
            if (!success) {
                logger.error("Failed to fetch endpoint={} after {} attempts for jobId={}", endpoint, MAX_ATTEMPTS, job.getJobId());
                // Record failure in job; mark partial success possibility
                if (job.getFailureReason() == null) job.setFailureReason("FETCH_ENDPOINT_FAILURE");
            }
        }

        // Update job state transition will be handled by workflow engine; just update times
        job.setLastAttemptAt(java.time.OffsetDateTime.now().toString());
        logger.info("FetchDataProcessor completed for jobId={}", job.getJobId());
        return job;
    }

    private void backoff(int attempt) {
        try {
            long wait = BASE_BACKOFF_MS * (1L << (attempt - 1));
            TimeUnit.MILLISECONDS.sleep(wait);
        } catch (InterruptedException ignored) {
        }
    }
}
