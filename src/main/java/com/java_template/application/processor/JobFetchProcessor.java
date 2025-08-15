package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JobFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public JobFetchProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing JobFetchProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for fetching")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && "FETCHING".equals(job.getStatus());
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        List<JsonNode> fetchedRecords = new ArrayList<>();

        String runId = null;
        try {
            if (job.getResultSummary() != null) {
                JsonNode rs = objectMapper.readTree(job.getResultSummary());
                if (rs.has("runId")) runId = rs.get("runId").asText();
            }
        } catch (Exception e) {
            logger.debug("Unable to parse runId from resultSummary for job {}: {}", job.getTechnicalId(), e.getMessage());
        }

        // Generic paged fetch loop. The sourceUrl may support simple page/limit parameters or next links.
        String nextUrl = job.getSourceUrl();
        int page = 0;
        int maxPages = 100; // safety limit

        while (nextUrl != null && page < maxPages) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(nextUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> resp = executeWithRetries(req, 3);
                int status = resp.statusCode();
                if (status >= 200 && status < 300) {
                    String body = resp.body();
                    JsonNode root = objectMapper.readTree(body);
                    ArrayNode pageArray = null;

                    if (root.isArray()) {
                        pageArray = (ArrayNode) root;
                    } else if (root.has("records") && root.get("records").isArray()) {
                        pageArray = (ArrayNode) root.get("records");
                    } else if (root.has("data") && root.get("data").isArray()) {
                        pageArray = (ArrayNode) root.get("data");
                    }

                    if (pageArray != null) {
                        for (JsonNode n : pageArray) fetchedRecords.add(n);
                    } else {
                        // attempt to interpret root as a single record
                        fetchedRecords.add(root);
                    }

                    // follow next link if present
                    if (root.has("next") && root.get("next").isTextual()) {
                        nextUrl = root.get("next").asText();
                    } else if (root.has("links") && root.get("links").has("next") && root.get("links").get("next").isTextual()) {
                        nextUrl = root.get("links").get("next").asText();
                    } else {
                        // no explicit next token; break after first page
                        nextUrl = null;
                    }

                    logger.info("Job {} fetched page {} with {} records", job.getTechnicalId(), page, (pageArray == null ? 1 : pageArray.size()));
                } else {
                    logger.warn("Job {} source returned status {} on page {}", job.getTechnicalId(), status, page);
                    // treat as transient and stop fetch
                    break;
                }

            } catch (Exception e) {
                logger.warn("Job {} failed to fetch page {}: {}", job.getTechnicalId(), page, e.getMessage());
                break;
            }
            page++;
        }

        // Attach a detailed JSON summary including fetchedRecords
        ObjectNode rs = objectMapper.createObjectNode();
        rs.put("fetched", fetchedRecords.size());
        rs.put("fetchedAt", Instant.now().toString());
        if (runId != null) rs.put("runId", runId);
        try {
            rs.putPOJO("fetchedRecords", fetchedRecords);
            job.setResultSummary(objectMapper.writeValueAsString(rs));
        } catch (Exception e) {
            logger.warn("Failed to write resultSummary for job {}: {}", job.getTechnicalId(), e.getMessage());
            job.setResultSummary("{}");
        }

        job.setStatus("NORMALIZING");
        logger.info("Job {} fetch complete, totalRecords={}", job.getTechnicalId(), fetchedRecords.size());

        return job;
    }

    private HttpResponse<String> executeWithRetries(HttpRequest req, int maxRetries) throws IOException, InterruptedException {
        int attempts = 0;
        long base = 500L; // ms
        while (true) {
            try {
                return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                attempts++;
                if (attempts >= maxRetries) throw e;
                try {
                    Thread.sleep(base * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            }
        }
    }
}
