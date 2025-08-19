package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.lookupjob.version_1.LookupJob;
import com.java_template.common.config.Config;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;

@Component
public class FetchUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FetchUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing LookupJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(LookupJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(LookupJob entity) {
        return entity != null && entity.isValid();
    }

    private LookupJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<LookupJob> context) {
        LookupJob job = context.entity();
        Integer userId = job.getUserId();
        Instant start = Instant.now();

        // Ensure lifecycle started
        try {
            if (job.getStartedAt() == null || job.getStartedAt().isBlank()) {
                job.setStartedAt(start.toString());
            }
            // set lifecycle state to IN_PROGRESS if not set, and mark FETCHED after attempt
            if (job.getLifecycleState() == null || job.getLifecycleState().isBlank()) {
                job.setLifecycleState("IN_PROGRESS");
            }
        } catch (Exception e) {
            logger.warn("FetchUserProcessor: error while setting start metadata for job={}: {}", job.getTechnicalId(), e.getMessage());
        }

        if (userId == null) {
            logger.warn("FetchUserProcessor: userId is null for job={}", job.getTechnicalId());
            // attach fetchResponse with 400
            job.setFetchResponse(createResponseNode(400, null, "userId missing", 0L, 0));
            job.setLastAttemptAt(Instant.now().toString());
            job.setLifecycleState("FETCHED");
            return job;
        }

        String urlStr = String.format("https://reqres.in/api/users/%d", userId);
        HttpURLConnection conn = null;
        int status = 0;
        String details = null;
        JsonNode bodyNode = null;
        long durationMs = 0L;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(Config.DEFAULT_TIMEOUT_MS);
            conn.setReadTimeout(Config.DEFAULT_TIMEOUT_MS);

            conn.setRequestProperty("Accept", "application/json");

            Instant callStart = Instant.now();
            status = conn.getResponseCode();
            durationMs = Duration.between(callStart, Instant.now()).toMillis();

            if (status == 200) {
                JsonNode root = objectMapper.readTree(conn.getInputStream());
                bodyNode = root.path("data");
            } else {
                // try to read error stream for details
                InputStream errStream = conn.getErrorStream();
                if (errStream != null) {
                    details = readStream(errStream);
                }
            }
        } catch (IOException e) {
            logger.debug("FetchUserProcessor network error for job={}: {}", job.getTechnicalId(), e.getMessage());
            details = e.getMessage();
            // Treat as retryable/transient
            status = 503;
        } catch (Exception e) {
            logger.error("FetchUserProcessor unexpected error for job={}: {}", job.getTechnicalId(), e.getMessage());
            details = e.getMessage();
            status = 500;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        // attach fetchResponse as JSON string with additional telemetry
        int nextAttempt = job.getAttempts() != null ? job.getAttempts() : 0;
        job.setFetchResponse(createResponseNode(status, bodyNode, details, durationMs, nextAttempt));
        job.setLastAttemptAt(Instant.now().toString());
        // mark fetched state so workflow can evaluate fetchResponse
        job.setLifecycleState("FETCHED");
        logger.info("FetchUserProcessor: job={} userId={} status={} durationMs={} attempts={}", job.getTechnicalId(), userId, status, durationMs, nextAttempt);
        return job;
    }

    private String readStream(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }

    private String createResponseNode(int status, JsonNode body, String details, long durationMs, int attempts) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("status", status);
            if (body != null && !body.isMissingNode()) {
                node.set("body", body);
            }
            if (details != null && !details.isBlank()) {
                node.put("details", details);
            }
            node.put("durationMs", durationMs);
            node.put("timestamp", Instant.now().toString());
            node.put("attempts", attempts);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            logger.debug("FetchUserProcessor: error creating response node: {}", e.getMessage());
            return String.format("{\"status\":%d,\"details\":\"%s\"}", status, details == null ? "" : details.replaceAll("\"", "'"));
        }
    }
}
