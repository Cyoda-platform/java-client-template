package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class FetchDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final int MAX_ATTEMPTS = 3;
    private final long BASE_BACKOFF_MS = 500L;
    private final ObjectMapper mapper = new ObjectMapper();

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

    @SuppressWarnings("unchecked")
    private ExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionJob> context) {
        ExtractionJob job = context.entity();
        Object paramsObj = job.getParameters();
        List<String> endpoints = null;
        if (paramsObj instanceof Map) {
            Object eps = ((Map) paramsObj).get("endpoints");
            if (eps instanceof List) {
                endpoints = new ArrayList<>();
                for (Object o : (List) eps) {
                    if (o != null) endpoints.add(String.valueOf(o));
                }
            }
        }

        if (endpoints == null || endpoints.isEmpty()) {
            logger.warn("No endpoints configured for jobId={}", job.getJobId());
            return job;
        }

        ArrayNode aggregatedProducts = mapper.createArrayNode();
        boolean anyFatal = false;

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
                    conn.setRequestProperty("Accept", "application/json");
                    int status = conn.getResponseCode();

                    if (status >= 200 && status < 300) {
                        try (InputStream is = conn.getInputStream()) {
                            byte[] bytes = is.readAllBytes();
                            String body = new String(bytes, StandardCharsets.UTF_8);
                            JsonNode root = mapper.readTree(body);
                            // If response contains products array, merge; else if root is array, treat as products
                            if (root.isObject() && root.has("products") && root.get("products").isArray()) {
                                ArrayNode arr = (ArrayNode) root.get("products");
                                arr.forEach(aggregatedProducts::add);
                            } else if (root.isArray()) {
                                ArrayNode arr = (ArrayNode) root;
                                arr.forEach(aggregatedProducts::add);
                            } else if (root.isObject()) {
                                // Try to map single product
                                aggregatedProducts.add(root);
                            }
                        }

                        logger.info("Successfully fetched {} for jobId={}", endpoint, job.getJobId());
                        success = true;
                    } else if (status >= 500 && status < 600) {
                        logger.warn("Transient error fetching {} status={} attempt={} for jobId={}", endpoint, status, attempt, job.getJobId());
                        backoffWithJitter(attempt);
                    } else if (status >= 400 && status < 500) {
                        logger.error("Client error fetching {} status={} for jobId={}", endpoint, status, job.getJobId());
                        anyFatal = true;
                        break;
                    } else {
                        logger.warn("Unexpected status {} fetching {} for jobId={}", status, endpoint, job.getJobId());
                        backoffWithJitter(attempt);
                    }

                } catch (Exception e) {
                    logger.warn("Error fetching endpoint={} attempt={} for jobId={}: {}", endpoint, attempt, job.getJobId(), e.getMessage());
                    backoffWithJitter(attempt);
                }
            }
            if (!success) {
                logger.error("Failed to fetch endpoint={} after {} attempts for jobId={}", endpoint, MAX_ATTEMPTS, job.getJobId());
                // Record failure in job; mark partial success possibility
                if (job.getParameters() instanceof Map) {
                    Map params = (Map) job.getParameters();
                    params.putIfAbsent("_fetchFailures", new ArrayList<String>());
                    try {
                        ((List) params.get("_fetchFailures")).add(endpoint);
                    } catch (Exception ignored) {}
                }
                if (job.getFailureReason() == null) job.setFailureReason("FETCH_ENDPOINT_FAILURE");
            }
        }

        // Attach aggregated products into job.parameters.rawData for downstream TransformProcessor
        if (job.getParameters() instanceof Map) {
            Map params = (Map) job.getParameters();
            ObjectNode raw = mapper.createObjectNode();
            raw.set("products", aggregatedProducts);
            params.put("rawData", raw);
        }

        // Update job times
        job.setLastRunAt(OffsetDateTime.now().toString());
        logger.info("FetchDataProcessor completed for jobId={}", job.getJobId());

        // If any fatal client errors occurred mark job as FAILED
        if (anyFatal) {
            job.setStatus("FAILED");
            job.setFailureReason("FETCH_FATAL_FAILURE");
        }

        return job;
    }

    private void backoffWithJitter(int attempt) {
        try {
            long base = BASE_BACKOFF_MS * (1L << (Math.max(0, attempt - 1)));
            long jitter = (long) (Math.random() * 200L);
            long wait = base + jitter;
            TimeUnit.MILLISECONDS.sleep(wait);
        } catch (InterruptedException ignored) {
        }
    }
}
